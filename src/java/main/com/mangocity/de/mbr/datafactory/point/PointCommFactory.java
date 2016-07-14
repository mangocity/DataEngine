package com.mangocity.de.mbr.datafactory.point;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mangocity.ce.bean.EngineBean;
import com.mangocity.ce.book.ConstantArgs;
import com.mangocity.ce.book.ErrorBook;
import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.ce.book.SysBook;
import com.mangocity.ce.deploy.ConfigManage;
import com.mangocity.ce.exception.BusinessException;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.exception.IllegalParamException;
import com.mangocity.ce.exception.SystemException;
import com.mangocity.ce.safe.SafeUtil;
import com.mangocity.ce.util.AssertUtils;
import com.mangocity.ce.util.CommonUtils;
import com.mangocity.ce.util.MbrPost;
import com.mangocity.ce.util.New;
import com.mangocity.de.mbr.book.SqlMapper;
import com.mangocity.de.mbr.book.SqlMapper.TransacOperation;
import com.mangocity.de.mbr.util.SignUtil;
import com.mangocity.de.mbr.util.SqlUtil;
import com.mangocity.de.mbr.util.Tools;

/**
 * 积分通用服务
 * 
 * @author mbr.yangjie
 */
public class PointCommFactory {
	private static final Logger log = Logger.getLogger(PointCommFactory.class);
	/**
	 * 默认超时时间(60S)
	 */
	private static final Long TIME_OUT_SECONDS = 60L;

	/**
	 * 查询芒果网本地积分
	 * 
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String,Object> queryLocalEnabledPoint(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommFactory queryLocalEnabledPoint begin()...param: " + pb.getHeadMap());
		// step1: 根据mbrId查询积分账户信息 并校验账户是否有效
		Map<String, Object> pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId",
				pb.getHeadMap());
		validPointAccount(pointAccountMap);

		// step2: 根据积分账户id查询积分余额总额信息 如果积分总额是有效数字,则把积分总额作为剩余积分总数返回给客户端
		pointAccountMap.put("accoutId", pointAccountMap.get("pointAccountId"));
		Long pointBalance = CommonUtils.objectToLong(
				SqlUtil.getInstance().selectOneString("queryPointBalanceByAccountId", pointAccountMap), -1L);
		if (-1L != pointBalance) {
			pointAccountMap.put("pointTotal", pointBalance);
		}
		log.info("accoutId: " + pointAccountMap.get("accoutId") + " ,pointTotal: " + pointAccountMap.get("pointTotal"));
		return pointAccountMap;
	}

	/**
	 * 查询集团用户积分 {"mbrId":"59108443"}
	 * 
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String, Object> queryCrmEnabledPoint(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommFactory queryCrmEnabledPoint begin()...param: " + pb.getHeadMap());
		//HTTP URL
		final StringBuilder sb = new StringBuilder();
		sb.append(ConfigManage.instance().getSysConfig("mq.engine.http.url.prefix"));
		sb.append(ConfigManage.instance().getSysConfig("queryCrmEnablePoint.url.suffix"));

		log.info("queryCrmEnabledPoint url: " + sb.toString());
		Map<String, Object> mbrMap = SqlUtil.getInstance().selectOne("queryMbrByMbrId", pb.getHeadMap());
		
		//构建请求参数
		final Map<String, Object> paramMap = New.map();
		paramMap.put("crmCustId", mbrMap.get("crmCustId"));
		//构建sign加密串
		final Map<String,Object> resultMap = buildMd5Sign(paramMap);
		log.info("do Http request param: " + resultMap);

		Map<String, Object> pointAccountMap = null;
		try {
			//单个线程请求HTTP服务,设置超时
			ExecutorService executorService = Executors.newSingleThreadExecutor();
			Future<String> future = executorService.submit(new Callable<String>() {
				@Override
				public String call() throws Exception {
					return MbrPost.doPost(CommonUtils.trim(sb.toString()), JSON.toJSONString(resultMap));
				}
			});
			final Long timeOut = CommonUtils.objectToLong(ConfigManage.instance().getSysConfig("http.timeout"), TIME_OUT_SECONDS);//如果非数字超时,则默认15S
			log.info("http.timeout: " + timeOut);
			String responseJson = future.get(timeOut, TimeUnit.SECONDS);//如果超时,则会抛出TimeoutException
			log.info("请求url: " + sb.toString() + " ,响应结果: " + responseJson);
			try {
				JSONObject jsonObj = JSON.parseObject(responseJson);
				if(null == jsonObj){
					log.info("MQEngine responseText is null..");
					throw new BusinessException(this, ErrorConstant.ERROR_NO_RESULT_DATA, "MQ查询集团积分无响应");
				}
				if(!SysBook.SUCCESS.equals(jsonObj.getString("resultCode"))){
					log.info("queryCrmEnabledPoint error. The reason is: " + jsonObj.getString("resultMsg"));
					throw new BusinessException(this, jsonObj.getString("resultCode"), jsonObj.getString("resultMsg"));
				}
				Object points = jsonObj.getJSONObject("bodyMap").get("points");
				log.info("crmEnablePoints: " + points);
				//兼容之前的API返回(queryLocalEnabledPoint)
				pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId",
						pb.getHeadMap());
				validPointAccount(pointAccountMap);
				pointAccountMap.put("pointTotal", points);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				if (e instanceof ExceptionAbstract) {
					throw (ExceptionAbstract) e;
				}else{
					throw new SystemException(this, SysBook.SYSTEM, "请求查询集团积分接口非正常返回: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			log.error("请求url: " + sb.toString() + "出错 , " + e.getMessage(), e);
			if(e instanceof ExecutionException){
				throw new SystemException(this, SysBook.SYSTEM, "查询集团积分接口Connection refused.");
			}else if (e instanceof ExceptionAbstract) {
				throw (ExceptionAbstract) e;
			}else if(e instanceof TimeoutException){
				throw new SystemException(this, SysBook.SYSTEM, "查询集团积分接口超时");
			}
			throw new SystemException(this, SysBook.SYSTEM, "请求查集团积分接口出错");
		}
		return pointAccountMap;
	}

	/**
	 * 扣减芒果网本地积分原子接口
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String, Object> cutLocalPoint(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommFactory cutLocalPoint begin()...param: " + pb.getHeadMap());
		Map<String, Object> headMap = pb.getHeadMap();
		Long points = CommonUtils.objectToLong(headMap.get("points"), -1L);
		if (points.intValue() <= 0) {
			log.info("points: " + headMap.get("points"));
			throw new BusinessException(this, ErrorConstant.Point.ERROR_CUT_POINTS_LESS_THAN_ZERO_20008, "扣减积分数额不能小于0");
		}
		if (CommonUtils.isBlank((String) headMap.get("transType"))) {
			headMap.put("transType", ConstantArgs.POINT_TRANS_TYPE_ADJUSTMENT);
		}
		if (CommonUtils.isBlank((String) headMap.get("adjustType"))) {
			headMap.put("adjustType", ConstantArgs.POINT_INT_ADJUSTTYPE);
		}
		if (CommonUtils.isBlank((String) headMap.get("transStatus"))) {
			headMap.put("transStatus", ConstantArgs.POINT_INT_TRANSSTATUS);
		}
		if (CommonUtils.isBlank((String) headMap.get("cTSPointSubType"))) {// TODO
			headMap.put("cTSPointSubType", ConstantArgs.CTS_USER_POINT_SUB_TYPE);
		}
		headMap.put("adjustReasonCode", 1130L);
		headMap.put("transactionSubType", "Product");
		Map<String, Object> pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId", headMap);
		validPointAccount(pointAccountMap);
		if (((BigDecimal) pointAccountMap.get("stus")).intValue() != 1) {
			log.info("stus: " + pointAccountMap.get("stus"));
			throw new BusinessException(this, ErrorConstant.Point.ERROR_STUS_INVALID_20001, "积分状态无效stus!=1");
		}
		log.info("--------------扣减积分------begin---------------");
		/******** 操作会员积分 核心方法 ********/
		operateMbrPoint(headMap, ConstantArgs.LOY_TXN_TYPE_CD_REDEMPTION, ConstantArgs.POINT_TRANS_ATTRIBUTE_MANGOCTIY,
				false);
		log.info("--------------扣减积分------stop---------------");
		Map<String, Object> resultMap = New.map();
		resultMap.put("cutPointSucc", true);
		return resultMap;
	}

	/**
	 * 扣减集团用户积分原子接口
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String, Object> cutCrmPoint(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommFactory cutCrmPoint begin()...param: " + pb.getHeadMap());
		Long oldCrmEnablePoints = CommonUtils.objectToLong(pb.getHead("oldCrmEnablePoints"), -1L);//记录扣减积分调用之前的集团剩余积分余额
		Map<String, Object> headMap = pb.getHeadMap();
		// step1: 根据mbrId或者会籍查询会员信息
		Long points = CommonUtils.objectToLong(headMap.get("points"), -1L);
		Long mbrId = CommonUtils.objectToLong(headMap.get("mbrId"), -1L);
		String oldMbrshipCd = String.valueOf(headMap.get("mbrshipCd"));
		
		if (CommonUtils.isBlank((String) headMap.get("transType"))) {
			headMap.put("transType", ConstantArgs.POINT_TRANS_TYPE_ADJUSTMENT);
		}
		if (CommonUtils.isBlank((String) headMap.get("adjustType"))) {
			headMap.put("adjustType", ConstantArgs.POINT_INT_ADJUSTTYPE);
		}
		if (CommonUtils.isBlank((String) headMap.get("transStatus"))) {
			headMap.put("transStatus", ConstantArgs.POINT_INT_TRANSSTATUS);
		}
		if (CommonUtils.isBlank((String) headMap.get("cTSPointSubType"))) {// TODO
			headMap.put("cTSPointSubType", ConstantArgs.CTS_USER_POINT_SUB_TYPE);
		}
		headMap.put("adjustReasonCode", 1130L);
		headMap.put("transactionSubType", "Product");

		Map<String, Object> mbrMap = queryMbrByMbrIdOrOldMbrshipCd(mbrId, oldMbrshipCd);
		headMap.put("mbrId", mbrMap.get("mbrId"));

		// step2:集团回传信息不能为空
		log.info("集团会员信息: " + mbrMap);
		if (CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmCustId")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmMbrId")))) {// 如果集团会员的crmCustId字段为空,则不能查询集团积分余额信息
			throw new BusinessException(this, ErrorConstant.ERROR_PARAM_NULL_10000, "crmCustId、crmMbrId都不能为空");
		}
		// step5:校验请求参数,准备调用集团MQ请求数据
		// 校验请求集团MQ参数 并封装MQ请求参数
		validateReqMQParamAndPrepareMQ(headMap, mbrMap);
		
		//构建MD5加密串
		final Map<String, Object> paramMap = buildMd5Sign(headMap);

		// step6: 调用MQ
		StringBuilder sb = new StringBuilder();
		sb.append(ConfigManage.instance().getSysConfig("mq.engine.http.url.prefix"));
		sb.append(ConfigManage.instance().getSysConfig("cutCrmPoint.url.suffix"));
		log.info("do http request url: " + sb.toString());

		JSONObject jsonObj = null;
		try {
			String responseJson = MbrPost.doPost(sb.toString(), JSON.toJSONString(paramMap));
			log.info("请求url: " + sb.toString() + " ,响应结果: " + responseJson);
			try {
				jsonObj = JSON.parseObject(responseJson);
			} catch (Exception e) {
				throw new SystemException(this, SysBook.SYSTEM, "请求扣减集团积分接口返回数据转换JSON出错");
			}
			if(null == jsonObj){
				log.info("MQEngine responseText is null..");
				throw new BusinessException(this, ErrorConstant.ERROR_NO_RESULT_DATA, "请求扣减集团积分无响应");
			}
			if(!SysBook.SUCCESS.equals(jsonObj.getString("resultCode"))){
				log.info("cutCrmPoint error. The reason is: " + jsonObj.getString("resultMsg"));
				throw new BusinessException(this, jsonObj.getString("resultCode"), jsonObj.getString("resultMsg"));
			}
		} catch (Exception e) {
			log.error("请求url: " + sb.toString() + "出错 , " + e.getMessage(), e);
			if (e instanceof ExceptionAbstract) {
				throw (ExceptionAbstract) e;
			}
			throw new SystemException(this, SysBook.SYSTEM, "请求扣减集团积分接口出错");
		}
		Map<String, Object> resultMap = New.map();
		boolean isCutCrmPointSucc = false;
		headMap.put("adjustReasonCode", "1130");//消费积分
		if(SysBook.SUCCESS.equals(String.valueOf(jsonObj.get("resultCode")))){
			log.info("检查是否扣积分成功...根据调用后的积分余额是否等于调用前的积分余额减去本次扣除的积分");
			//在查一次集团积分余额,确保扣积分成功
			Long newCrmEnablePoints = CommonUtils.objectToLong(this.queryCrmEnabledPoint(pb).get("pointTotal"), -1L);
			log.info("开始扣积分之前的积分余额是: " + oldCrmEnablePoints + " ,本次扣减: " + points + " ,扣积分之后的余额: " + newCrmEnablePoints);
			//如果扣除后的积分加上本次要扣除的积分等于调用之前的积分,则说明扣除积分成功
			if(newCrmEnablePoints.intValue() == (oldCrmEnablePoints.intValue() - points.intValue())){
				resultMap.put("cutPointSucc", true);
				isCutCrmPointSucc = true;
				writePointTransaction(headMap,ConstantArgs.LOY_TXN_TYPE_CD_REDEMPTION,isCutCrmPointSucc);//写入积分明细
				return resultMap;
			}
		}
		isCutCrmPointSucc = false;
		writePointTransaction(headMap,ConstantArgs.LOY_TXN_TYPE_CD_REDEMPTION,isCutCrmPointSucc);
		throw new SystemException(this, SysBook.SYSTEM, "扣减集团积分失败");
	}

	//构建加密 
	private Map<String, Object> buildMd5Sign(Map<String, Object> headMap) {
		headMap.put("timestamp", CommonUtils.generateSpecifyLengthRandomNumber(8));
		headMap = SignUtil.paraFilter(headMap);//过滤"",null,Date类型的数据
		List<String> paramList = new ArrayList<String>(headMap.keySet());
		Collections.sort(paramList);
		StringBuilder sb = new StringBuilder();
		for(String key : paramList){
			sb.append(key);
			sb.append("=");
			sb.append(headMap.get(key));
			sb.append("&");
		}
		sb.deleteCharAt(sb.toString().length()-1);
		headMap.put("sign", SafeUtil.MD5(sb.toString()));
		log.info("de_sign_key: " + sb.toString() + " ,buildMD5Sign: " + SafeUtil.MD5(sb.toString()));
		return headMap;
	}
	
	//扣减集团积分后(不管成功 与否都写入积分明细)
	private void writePointTransaction(Map<String, Object> headMap,String transactionType, boolean isCutCrmPointSucc) throws ExceptionAbstract {
		log.info("writePointTransaction begin()...");
		SqlMapper sqlMapper = null;
		List<SqlMapper> sqlMapperList = new ArrayList<SqlMapper>();
		//step1:写入积分明细
		Map<String, Object> mbrMap = SqlUtil.getInstance().selectOne("queryMbrByMbrId", headMap);
		Map<String, Object> pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId", mbrMap);
		if(null == pointAccountMap){
			throw new BusinessException(this, ErrorConstant.Point.ERROR_ACCOUNT_NOT_EXIST_20002, "积分账户不存在");
		}
		Map<String, Object> transactionMap = new HashMap<String, Object>();
		Long tranId = (Long) SqlUtil.getInstance().selectOneString("queryPointTranId", null);
		String transAttribute = null;
		// 设置积分流水
		if(isCutCrmPointSucc){
			transAttribute = ConstantArgs.POINT_TRANS_ATTRIBUTE_CRM_SUCCESS;
		}else{
			transAttribute = ConstantArgs.POINT_TRANS_ATTRIBUTE_CRM_FAIL;
		}
		setPointTransaction(headMap, transactionMap, CommonUtils.objectToLong(pointAccountMap.get("pointAccountId"), 1L), tranId, transactionType, "MEMBER API", transAttribute);
		log.info("insertPointTransaction begin()...param: " + transactionMap + " ,time: " + new Date());
		sqlMapper = SqlMapper.getInstance().sqlId("insertPointTransaction").sqlType(TransacOperation.INSERT)
				.paramMap(transactionMap).build();
		sqlMapperList.add(sqlMapper);
		this.setAdjustmentBean(headMap, transactionMap, tranId);

		/*** 操作积分调整表 **/
		log.info("insertPointAdjustment begin()...param: " + transactionMap + " ,time: " + new Date());
		sqlMapper = SqlMapper.getInstance().sqlId("insertPointAdjustment").sqlType(TransacOperation.INSERT)
				.paramMap(transactionMap).build();
		sqlMapperList.add(sqlMapper);
		int row = SqlUtil.getInstance().transactionAll(sqlMapperList);
		if(row<=0){
			log.info("writePointTransaction失败...");
		}
		log.info("writePointTransaction end()...");
	}
	
	/**
	 * 增加芒果网本地积分原子接口
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String, Object> increaseLocalPoint(EngineBean pb) throws ExceptionAbstract {
		 Map<String,Object> headMap = pb.getHeadMap();
		log.info("increaseLocalPoint...begin()...param: " + headMap);
		Long points = CommonUtils.objectToLong(headMap.get("points"), -1L);
		String salesTransNO = String.valueOf(headMap.get("salesTransNO"));
		if(points<=0){
			throw new BusinessException(this, ErrorConstant.Point.ERROR_CUT_POINTS_LESS_THAN_ZERO_20008, "积分数必须大于0");
		}
		Long mbrId = CommonUtils.objectToLong(headMap.get("mbrId"), -1L);
		log.info(String.format("正在退还积分【mbrId: %s,salesTransNO： %s ,points: %s】", mbrId,headMap.get("salesTransNO"),headMap.get("points")));
		
		//step1: 如果指定的参数没有传入,则设置为默认值
		setDefaultValueIfNull(headMap);
		
		/********防止出现退还积分大于扣减的情况*********/
		try {
		//step2: 根据订单号获取该笔订单已经退还的积分和实际扣除的积分
			if (CommonUtils.isNotBlank(salesTransNO) && !salesTransNO.startsWith("CTP")) {//CTP 开头的订单号代表现金转积分的订单，不做校验
				Long sumPayment = (Long) SqlUtil.getInstance().selectOneString("getSumPaymentByOrderNo", headMap);//获取该笔订单实际扣减的积分总数
				Long sumRefund = (Long) SqlUtil.getInstance().selectOneString("getSumRefundByOrderNo", headMap);//获取已经退还的积分数
				log.info("salesTransNO: " + headMap.get("salesTransNO") + " ,该订单扣减的积分数是: " + sumPayment
						+ " ,已经退还的积分是: " + sumRefund + " ,本次要退还的积分: " + points);
				if (null == sumPayment) {
					sumPayment = 0L;
				}
				if (null == sumRefund) {
					sumRefund = 0L;
				}
				//step3:如果退还积分大于扣减,则直接抛出自定义异常
				if (points + sumRefund > sumPayment) {
					throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_REFUND_MORETHAN_CUT_20011,
							"退还积分大于扣减积分");
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if( e instanceof ExceptionAbstract) {
				ExceptionAbstract ea = (ExceptionAbstract)e;
				throw ea;
			}else{
				throw new SystemException(this, ErrorBook.OTHER_ERROR, "数据库查询错误");
			}
		}
		
		//step3:根据mbrId查询积分账户信息
		Map<String,Object> pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId", headMap);
		validPointAccount(pointAccountMap);//校验积分账户
		headMap.put("adjustReasonCode", 1131L);
		
		//step4:开始退还积分
		operateMbrPoint(headMap,ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL,
				ConstantArgs.POINT_TRANS_ATTRIBUTE_MANGOCTIY,false);
		Map<String,Object> resultMap = New.map(); 
		resultMap.put("increasePointSucc", true);
		log.info("increaseLocalPoint end()...");
		return resultMap;
	}

	/**
	 * 扣减集团用户积分原子接口
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String, Object> increaseCrmPoint(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommFactory increaseCrmPoint begin()...param: " + pb.getHeadMap());
		Long oldCrmEnablePoints = CommonUtils.objectToLong(pb.getHead("oldCrmEnablePoints"), -1L);//记录扣减积分调用之前的集团剩余积分余额
		Map<String, Object> headMap = pb.getHeadMap();
		// step1: 根据mbrId或者会籍查询会员信息
		Long points = CommonUtils.objectToLong(headMap.get("points"), -1L);
		Long mbrId = CommonUtils.objectToLong(headMap.get("mbrId"), -1L);
		String oldMbrshipCd = String.valueOf(headMap.get("mbrshipCd"));

		Map<String, Object> mbrMap = queryMbrByMbrIdOrOldMbrshipCd(mbrId, oldMbrshipCd);
		headMap.put("mbrId", mbrMap.get("mbrId"));

		// step2:集团回传信息不能为空
		log.info("集团会员信息: " + mbrMap);
		if (CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmCustId")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmMbrId")))) {// 如果集团会员的crmCustId字段为空,则不能查询集团积分余额信息
			throw new BusinessException(this, ErrorConstant.ERROR_PARAM_NULL_10000, "crmCustId、crmMbrId都不能为空");
		}
		//设置crmCustId参数,查询集团积分余额
		/*pb.setHead("crmCustId", mbrMap.get("crmCustId"));
		pb.setHead("crmMbrId", mbrMap.get("crmMbrId"));
		pb.setHead("crmMbrshipCd", mbrMap.get("crmMbrshipCd"));*/
		
		// step5:校验请求参数,准备调用集团MQ请求数据
		// 校验请求集团MQ参数 并封装MQ请求参数
		validateReqMQParamAndPrepareMQ(headMap, mbrMap);
		
		//构建MD5加密串
		final Map<String,Object> paramMap = buildMd5Sign(headMap);//必须重新申明一个变量

		// step6: 调用MQ
		StringBuilder sb = new StringBuilder();
		sb.append(ConfigManage.instance().getSysConfig("mq.engine.http.url.prefix"));
		sb.append(ConfigManage.instance().getSysConfig("increaseCrmPoint.url.suffix"));
		log.info("do http request url: " + sb.toString());

		JSONObject jsonObj = null;
		try {
			String responseJson = MbrPost.doPost(sb.toString(), JSON.toJSONString(paramMap));
			log.info("请求url: " + sb.toString() + " ,响应结果: " + responseJson);
			try {
				jsonObj = JSON.parseObject(responseJson);
			} catch (Exception e) {
				throw new SystemException(this, SysBook.SYSTEM, "请求退还集团积分接口返回数据转换JSON出错");
			}
			if(null == jsonObj){
				log.info("MQEngine responseText is null..");
				throw new BusinessException(this, ErrorConstant.ERROR_NO_RESULT_DATA, "请求退还集团积分无响应");
			}
			if(!SysBook.SUCCESS.equals(jsonObj.getString("resultCode"))){
				log.info("increaseCrmPoint error. The reason is: " + jsonObj.getString("resultMsg"));
				throw new BusinessException(this, jsonObj.getString("resultCode"), jsonObj.getString("resultMsg"));
			}
		} catch (Exception e) {
			log.error("请求url: " + sb.toString() + "出错 , " + e.getMessage(), e);
			if (e instanceof ExceptionAbstract) {
				throw (ExceptionAbstract) e;
			}
			throw new SystemException(this, SysBook.SYSTEM, "请求退还集团积分接口出错");
		}
		Map<String, Object> resultMap = New.map();
		boolean isCutCrmPointSucc = false;
		headMap.put("adjustReasonCode", "1131");//取消消费退回积分
		if(SysBook.SUCCESS.equals(String.valueOf(jsonObj.get("resultCode")))){
			log.info("检查是否退还积分成功...根据调用后的积分余额是否等于调用前的积分余额加上本次退还的积分");
			//在查一次集团积分余额,确保扣积分成功
			Long newCrmEnablePoints = CommonUtils.objectToLong(this.queryCrmEnabledPoint(pb).get("pointTotal"), -1L);
			log.info("开始退还积分之前的积分余额是: " + oldCrmEnablePoints + " ,本次退还: " + points + " ,退还积分之后的余额: " + newCrmEnablePoints);
			//如果扣除后的积分加上本次要扣除的积分等于调用之前的积分,则说明扣除积分成功
			if(newCrmEnablePoints.intValue() == (oldCrmEnablePoints.intValue() + points.intValue())){
				resultMap.put("increasePointSucc", true);
				isCutCrmPointSucc = true;
				writePointTransaction(headMap,ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL,isCutCrmPointSucc);//写入积分明细
				return resultMap;
			}
		}
		isCutCrmPointSucc = false;
		writePointTransaction(headMap,ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL,isCutCrmPointSucc);//写入积分明细
		throw new SystemException(this, SysBook.SYSTEM, "退还集团积分失败");
	}
	
	private void setDefaultValueIfNull(Map<String, Object> headMap) {
		if(CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("transType")))){
			headMap.put("transType", ConstantArgs.POINT_TRANS_TYPE_ADJUSTMENT);
		}
		if(CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("adjustType")))){
			headMap.put("adjustType", ConstantArgs.POINT_INT_ADJUSTTYPE);
		}
		if(CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("transStatus")))){
			headMap.put("transStatus", ConstantArgs.POINT_INT_TRANSSTATUS);
		}
		if(CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("cTSPointSubType")))){
			headMap.put("cTSPointSubType", ConstantArgs.CTS_CONSUME_POINT_SUB_TYPE);
		}
	}


	// 校验请求集团MQ参数 并封装MQ请求参数
	private void validateReqMQParamAndPrepareMQ(Map<String, Object> headMap, Map<String, Object> mbrMap)
			throws ExceptionAbstract {
		// step1:设置集团访问参数
		headMap.put("crmMbrId", mbrMap.get("crmMbrId"));
		headMap.put("crmMbrshipCd", mbrMap.get("crmMbrshipCd"));
		headMap.put("crmCustId", mbrMap.get("crmCustId"));
		// step2.校验参数
		if (CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmMbrId")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmMbrshipCd")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(mbrMap.get("crmCustId")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("points")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("transactionChannel")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("cTSTransactionChannelSN")))
				|| CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("cTSTransactionOrgCode")))) {
			throw new BusinessException(this, ErrorBook.PARAM_ESS_ERROR, "请求集团MQ参数必须填完整");
		}
		// 交易号 动态生成 取的主键值
		String transactionNum = (String) SqlUtil.getInstance().selectOneString("getPointAwardId", null);
		if(CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("transactionNumber")))){//兼容测试环境和生产环境调用集团(该参数必须唯一,不然集团会报错)
			headMap.put("transactionNumber", transactionNum);
		}
		log.info("请求集团MQ积分参数: " + headMap);
	}

	// 根据mbrId或者会籍编码查询会员信息
	private Map<String, Object> queryMbrByMbrIdOrOldMbrshipCd(Long mbrId, String oldMbrshipCd)
			throws ExceptionAbstract, BusinessException {
		Map<String, Object> mbrMap = null;
		Map<String, Object> paramMap = New.map();
		if (CommonUtils.isEmpty(mbrId) || mbrId == -1L) {
			paramMap.put("mbrshipCd", oldMbrshipCd);
			mbrMap = SqlUtil.getInstance().selectOne("queryMbrByMbrshipCd", paramMap);// 根据会籍查询会员信息
			if (null == mbrMap) {
				log.info("mbr: " + mbrMap + "mbrshipCd: " + oldMbrshipCd);
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "根据会籍查询不到该会员");
			}
			mbrId = CommonUtils.objectToLong(mbrMap.get("mbrId"), -1L);
			paramMap.put("mbrId", mbrId);
		} else {
			paramMap.put("mbrId", mbrId);
		}
		mbrMap = SqlUtil.getInstance().selectOne("queryMbrByMbrId", paramMap);
		if (null == mbrMap) {
			log.info("mbr: " + mbrMap + "mbrId: " + paramMap.get("mbrId"));
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "根据该mbrId查询不到会员");
		}
		return mbrMap;
	}

	// 校验积分账户是否正常
	private void validPointAccount(Map<String, Object> pointAccountMap) throws IllegalParamException, BusinessException {
		log.info("积分账户信息:" + pointAccountMap);
		AssertUtils.assertNull(pointAccountMap, "不存在该会员id对应的积分账户信息");
		int stus = ((BigDecimal) pointAccountMap.get("stus")).intValue();
		if (stus == 2) {
			log.info(pointAccountMap + "积分账户已冻结");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_ACCOUNT_FREEZE_20005, "积分账户已冻结");
		} else if (stus == 3) {
			log.info(pointAccountMap + "积分账户已注销");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_ACCOUNT_Logout_20006, "积分账户已注销");
		} else if (stus == 0) {
			log.info(pointAccountMap + "积分账户未激活");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_ACCOUNT_NOT_ACCTIVATED_20007, "积分账户未激活");
		}
	}

	// 操作会员积分
	@SuppressWarnings("unused")
	private void operateMbrPoint(Map<String, Object> headMap, String adjustReasonCode, String transAttribute,
			boolean isCrm) throws ExceptionAbstract {
		log.info("operatMbrPoint begin()...");
		String transType = (String) headMap.get("transType");
		String updateBy = (String) headMap.get("updateBy");
		Long mbrId = CommonUtils.objectToLong(headMap.get("mbrId"), -1L);
		Long transPointValue = CommonUtils.objectToLong(headMap.get("points"), -1L);
		Long accountId = -1L;
		Long pointTotal = null;
		Long targetVersion = null;
		boolean operateFlag = false;
		boolean transactionFlag = false;
		Long operateAfterVersion = null;
		Long tranId = null;

		// 处理事务
		SqlMapper sqlMapper = null;
		List<SqlMapper> sqlMapperList = new ArrayList<SqlMapper>();
		Map<String, Object> transactionMap = new HashMap<String, Object>();
		Map<String, Object> pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId", headMap);
		int stus = ((BigDecimal) pointAccountMap.get("stus")).intValue();
		if (stus == 2) {
			log.info(pointAccountMap + "积分账户已冻结");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_ACCOUNT_FREEZE_20005, "积分账户已冻结");
		} else if (stus == 3) {
			log.info(pointAccountMap + "积分账户已注销");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_ACCOUNT_Logout_20006, "积分账户已注销");
		} else if (stus == 0) {
			log.info(pointAccountMap + "积分账户未激活");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_POINT_ACCOUNT_NOT_ACCTIVATED_20007, "积分账户未激活");
		}
		if (CommonUtils.isNotEmpty(pointAccountMap)) {
			accountId = CommonUtils.objectToLong(pointAccountMap.get("pointAccountId"), -1L);
			headMap.put("accoutId", accountId);
			if (CommonUtils.isNotEmpty(pointAccountMap.get("pointTotal"))) {// 查询积分余额
				pointTotal = (Long) (SqlUtil.getInstance().selectOneString("queryPointBalanceByAccountId", headMap));
				if (null == pointTotal) {
					pointTotal = 0L;
				}
				log.info("扣减积分------>mbrId: " + mbrId.intValue() + " ,accoutId: " + accountId + " ,pointTotal: "
						+ pointTotal + " ,所扣积分数: " + transPointValue);
			}
		} else {
			log.info("operateMbrPoint pointAccount is null");
			throw new BusinessException(this, ErrorConstant.Point.ERROR_ACCOUNT_NOT_EXIST_20002, "积分账户不存在");
		}
		if (CommonUtils.isNotBlank(transType)) {
			targetVersion = (Long) SqlUtil.getInstance().selectOneString("queryVersionByMbrId", headMap);

			/******** 添加或者扣减积分入口 *******/
			log.info("addAndCutPoint begin()...time: " + new Date());
			addAndCutPoint(sqlMapperList, pointTotal, updateBy, mbrId, transType, transPointValue, adjustReasonCode,
					isCrm);

			// 操作积分--积分余额表
			try {
				String postingDateTime = (String) headMap.get("postingDateTime");

				/********* 操作积分余额 ********/
				log.info("operatePointBalance begin()...time: " + new Date());
				operatePointBalance(sqlMapperList, mbrId, accountId, updateBy, adjustReasonCode, transPointValue,
						CommonUtils.isNotBlank(postingDateTime) ? CommonUtils.parseDate(postingDateTime, "yyyyMMdd")
								: null);
			} catch (ParseException e) {
				log.error(e.getMessage());
				throw new SystemException(this, ErrorBook.OTHER_ERROR, "createMonth日期转换错误");
			}
			// 获取操作之后的版本号
			operateAfterVersion = (Long) SqlUtil.getInstance().selectOneString("queryVersionByMbrId", headMap);
			// 版本号不一致，终止操作
			if (!targetVersion.toString().equals(operateAfterVersion.toString())) {
				throw new BusinessException(this, ErrorBook.OTHER_ERROR, "操作会员积分,版本号不一致");
			} else {
				// 相同，操作继续，版本号加一
				operateAfterVersion += 1;
				headMap.put("version", operateAfterVersion);
				sqlMapper = SqlMapper.getInstance().sqlId("updateVersionByMbrId").sqlType(TransacOperation.UPDATE)
						.paramMap(headMap).build();
				sqlMapperList.add(sqlMapper);
			}
			tranId = (Long) SqlUtil.getInstance().selectOneString("queryPointTranId", null);
			// 设置积分流水
			setPointTransaction(headMap, transactionMap, accountId, tranId, adjustReasonCode, updateBy, transAttribute);

			/*********** 操作积分明细 *********/
			log.info("insertPointTransaction begin()...param: " + transactionMap + " ,time: " + new Date());
			sqlMapper = SqlMapper.getInstance().sqlId("insertPointTransaction").sqlType(TransacOperation.INSERT)
					.paramMap(transactionMap).build();
			sqlMapperList.add(sqlMapper);
			this.setAdjustmentBean(headMap, transactionMap, tranId);

			/*** 操作积分调整表 **/
			log.info("insertPointAdjustment begin()...param: " + transactionMap + " ,time: " + new Date());
			sqlMapper = SqlMapper.getInstance().sqlId("insertPointAdjustment").sqlType(TransacOperation.INSERT)
					.paramMap(transactionMap).build();
			sqlMapperList.add(sqlMapper);

			int row = SqlUtil.getInstance().transactionAll(sqlMapperList);
			if (row <= 0) {
				throw new DatabaseException(this, ErrorConstant.ERROR_TRANSACTION_ROLL_BACK_10006, "扣减积分事务提交失败");
			}
			log.info("operatMbrPoint success...");

		}
	}
	
	/**
	 *  {
	 *  	"MGO02000058921372":"xx",
	    	"selCode":"00026216371",//流水
	    	"points":"100",
	 		}
	 * 积分互换
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String,Object> convertPointByRefund(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommonFactory convertPointByRefund begin()...appId: " + pb.getAppId() + " ,params: "
				+ pb.getHeadMap());
		pb.setHead("transType", ConstantArgs.POINT_TRANS_TYPE_CONVERT);
		pb.setHead("transStatus", ConstantArgs.POINT_INT_TRANSSTATUS);
		pb.setHead("cTSPointSubType", ConstantArgs.CTS_CONVERT_POINT_SUB_TYPE);
		//step1:开始互换
		Map<String,Object> headMap = pb.getHeadMap();
		String transType = (String) headMap.get("transType");
		String updateBy = (String) headMap.get("updateBy");
		Long mbrId = CommonUtils.objectToLong(headMap.get("mbrId"), -1L);
		Long transPointValue = CommonUtils.objectToLong(headMap.get("points"), -1L);
		Long accountId = -1L;
		Long pointTotal = null;
		Long targetVersion = null;
		boolean operateFlag = false;
		boolean transactionFlag = false;
		Long operateAfterVersion = null;
		Long tranId = null;
		
		/**
		 * 调整原因编码
		 */
		String adjustReasonCode = String.valueOf(pb.getHead("adjustReasonCode"));
		if(CommonUtils.isBlankIncludeNull(adjustReasonCode)){
			pb.setHead("adjustReasonCode", ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL);
			adjustReasonCode = ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL;
		}

		// 处理事务
		SqlMapper sqlMapper = null;
		List<SqlMapper> sqlMapperList = new ArrayList<SqlMapper>();
		Map<String, Object> transactionMap = new HashMap<String, Object>();
		Map<String, Object> pointAccountMap = SqlUtil.getInstance().selectOne("queryPointAccountByMbrId", headMap);
		validPointAccount(pointAccountMap);
		
		//如果积分账户存在,则将积分余额表中的可用 余额设置到pointTotal当中
		if (CommonUtils.isNotEmpty(pointAccountMap)) {
			accountId = CommonUtils.objectToLong(pointAccountMap.get("pointAccountId"), -1L);
			headMap.put("accoutId", accountId);
			if (CommonUtils.isNotEmpty(pointAccountMap.get("pointTotal"))) {// 查询积分余额
				pointTotal = (Long) (SqlUtil.getInstance().selectOneString("queryPointBalanceByAccountId", headMap));
				if (null == pointTotal) {
					pointTotal = 0L;
				}
				log.info("[互换积分] mbrId: " + mbrId.intValue() + " ,accoutId: " + accountId + " ,pointTotal: "
						+ pointTotal + " ,所换积分数: " + transPointValue);
			}
		} else {
			throw new BusinessException(this, ErrorConstant.Point.ERROR_ACCOUNT_NOT_EXIST_20002, "积分账户不存在");
		}
			targetVersion = (Long) SqlUtil.getInstance().selectOneString("queryVersionByMbrId", headMap);

			/******** 添加或者扣减积分入口 *******/
			log.info("addAndCutPoint begin()...time: " + new Date());
			addAndCutPoint(sqlMapperList, pointTotal, updateBy, mbrId, transType, transPointValue, adjustReasonCode,
					false);

			// 操作积分--积分余额表
			try {
				String postingDateTime = (String) headMap.get("postingDateTime");

				/********* 操作积分余额 ********/
				log.info("operatePointBalance begin()...time: " + new Date());
				operatePointBalance(sqlMapperList, mbrId, accountId, updateBy,adjustReasonCode, transPointValue,
						CommonUtils.isNotBlank(postingDateTime) ? CommonUtils.parseDate(postingDateTime, "yyyyMMdd")
								: null);
			} catch (ParseException e) {
				log.error(e.getMessage());
				throw new SystemException(this, ErrorBook.OTHER_ERROR, "createMonth日期转换错误");
			}
			// 获取操作之后的版本号
			operateAfterVersion = (Long) SqlUtil.getInstance().selectOneString("queryVersionByMbrId", headMap);
			// 版本号不一致，终止操作
			if (!targetVersion.toString().equals(operateAfterVersion.toString())) {
				throw new BusinessException(this, ErrorBook.OTHER_ERROR, "操作会员积分,版本号不一致");
			} else {
				// 相同，操作继续，版本号加一
				operateAfterVersion += 1;
				headMap.put("version", operateAfterVersion);
				sqlMapper = SqlMapper.getInstance().sqlId("updateVersionByMbrId").sqlType(TransacOperation.UPDATE)
						.paramMap(headMap).build();
				sqlMapperList.add(sqlMapper);
			}
			//交易id
			tranId = (Long) SqlUtil.getInstance().selectOneString("queryPointTranId", null);
			// 设置积分流水
			setPointTransaction(headMap, transactionMap, accountId, tranId, adjustReasonCode, updateBy, ConstantArgs.POINT_TRANS_ATTRIBUTE_MANGOCTIY);

			/*********** 操作积分明细 *********/
			log.info("insertPointTransaction begin()...param: " + transactionMap + " ,time: " + new Date());
			sqlMapper = SqlMapper.getInstance().sqlId("insertPointTransaction").sqlType(TransacOperation.INSERT)
					.paramMap(transactionMap).build();
			sqlMapperList.add(sqlMapper);

			/*** 插入积分互换记录 **/
			Long pointConvertId = CommonUtils.objectToLong(SqlUtil.getInstance().selectOneString("getSeqMbrPointConvert", null), -1L);
			headMap.put("pointConvertId", pointConvertId);
			headMap.put("pointTransactionId", tranId);
			headMap.put("tranStus", "O");//未完成,等操作完集团,则回调设置该值
			log.info("insertPointConvert begin()...param: " + headMap + " ,time: " + new Date());
			sqlMapper = SqlMapper.getInstance().sqlId("insertPointConvert").sqlType(TransacOperation.INSERT)
					.paramMap(headMap).build();
			sqlMapperList.add(sqlMapper);

			int row = SqlUtil.getInstance().transactionAll(sqlMapperList);
			if (row <= 0) {
				throw new DatabaseException(this, ErrorConstant.ERROR_TRANSACTION_ROLL_BACK_10006, "积分互换失败");
			}
			Map<String,Object> resultMap = New.map();
			resultMap.put("transNo", String.valueOf(tranId));
			resultMap.put("transDate", new Date());
			resultMap.put("points", String.valueOf(transPointValue));
			resultMap.put("selCode", headMap.get("selCode"));
			resultMap.put("pointConvertId", String.valueOf(pointConvertId));
			log.info("convertPoint success...");
			return resultMap;
	}
	
	// 设置积分调整信息
	private void setAdjustmentBean(Map<String, Object> headMap, Map<String, Object> transactionMap, Long tranId) {
		transactionMap.put("pointTransactionId", tranId);
		transactionMap.put("adjustType", headMap.get("adjustType"));
		transactionMap.put("refSalesTransNO", headMap.get("salesTransNO"));
		transactionMap.put("adjustRemarks", headMap.get("adjustRemarks"));
		transactionMap.put("adjustReasonCode", headMap.get("adjustReasonCode"));
		transactionMap.put("productCode", headMap.get("partNumber"));
		transactionMap.put("createBy", "MEMBER API");
		transactionMap.put("createTime", new Date());
	}

	// 设置积分流水
	private void setPointTransaction(Map<String, Object> headMap, Map<String, Object> transactionMap, Long accountId,
			Long tranId, String transactionType, String updateBy, String transAttribute) throws ExceptionAbstract {
		transactionMap.put("pointTransactionId", tranId);
		transactionMap.put("accountId", accountId);
		transactionMap.put("transType", headMap.get("transType"));

		Map<String, Object> mbrshipMap = null;
		Map<String, Object> mbrMap = null;
		if (CommonUtils.isBlankIncludeNull(String.valueOf(headMap.get("mbrshipCd")))) {
			if (CommonUtils.isNotEmpty(headMap.get("mbrId"))) {
				mbrMap = SqlUtil.getInstance().selectOne("queryMbrByMbrId", headMap);
				if (CommonUtils.isNotEmpty(mbrMap)) {
					mbrMap.put("mbrshipCd", mbrMap.get("defaultMbrshipCd"));// 根据默认会籍编码查询会籍信息
					mbrshipMap = SqlUtil.getInstance().selectOne("queryMbrShipByMbrshipCd", mbrMap);
					if(null != mbrshipMap){
						transactionMap.put("csn", mbrshipMap.get("mbrId"));
						transactionMap.put("mbrshipId", mbrshipMap.get("mbrshipId"));
						transactionMap.put("mbrshipCd", mbrshipMap.get("oldMbrshipCd"));
					}else{
						log.info("queryMbrShipByMbrshipCd 会籍信息为空...");
					}
				}
			}
		} else {
			mbrshipMap = SqlUtil.getInstance().selectOne("queryMbrShipByMbrshipCd", headMap);
			transactionMap.put("csn", mbrshipMap.get("MBR_ID"));
			transactionMap.put("mbrshipId", mbrshipMap.get("MBRSHIP_ID"));
			transactionMap.put("mbrshipCd", mbrshipMap.get("MBRSHIP_CD"));
		}

		if (transactionType.equals(ConstantArgs.LOY_TXN_TYPE_CD_REDEMPTION)) {
			transactionMap.put("transPointValue",
					Tools.addBearPoints(CommonUtils.objectToLong(headMap.get("points"), 0L)));
		} else if (transactionType.equals(ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL)) {
			transactionMap.put("transPointValue", CommonUtils.objectToLong(headMap.get("points"), 0L));
		}
		transactionMap.put("transAttribute", transAttribute);
		transactionMap.put("transStatus", headMap.get("transStatus"));
		transactionMap.put("createBy", updateBy);
		transactionMap.put("updateBy", updateBy);
		transactionMap.put("crmTransType", headMap.get("cTSPointSubType"));
	}

	// 操作积分余额
	private void operatePointBalance(List<SqlMapper> sqlMapperList, Long mbrId, Long accountId, String updateBy,
			String adjustReasonCode, Long transPointValue, Date expirydate) throws SystemException {
		boolean flag = false;
		Date newDate;
		Date date = null;
		String newExpirydate = null;
		Map<String, Object> pointBalanceMap = new HashMap<String, Object>();
		pointBalanceMap.put("mbrId", mbrId);
		pointBalanceMap.put("acctId", accountId);
		pointBalanceMap.put("pointBalance", transPointValue);
		pointBalanceMap.put("stus", ConstantArgs.POINT_BALANCE_STUS_USERLESS);
		pointBalanceMap.put("createBy", updateBy);
		pointBalanceMap.put("updateBy", updateBy);
		try {
			newDate = CommonUtils.parseDate(CommonUtils.format(new Date(), "yyyyMMdd"), "yyyyMMdd");
			pointBalanceMap.put("createMonth", newDate);
			if (CommonUtils.isEmpty(expirydate)) {
				Date cur = CommonUtils.getCurrentMonthLastDate();
				cur = CommonUtils.addYears(cur, 2);
				date = CommonUtils.parseDate(CommonUtils.format(cur, "yyyyMMdd"), "yyyyMMdd");
				pointBalanceMap.put("expirydate", date);// 过期时间,例如今天20150907,则过期时间则为20170930
			} else {
				pointBalanceMap.put("expirydate", expirydate);
				date = expirydate;
				log.info("积分过期时间 expirydate：" + expirydate);
			}
		} catch (ParseException e) {
			log.error(e.getMessage());
			throw new SystemException(this, ErrorBook.OTHER_ERROR, "createMonth日期转换错误");
		}
		try {
			if (adjustReasonCode.equals(ConstantArgs.LOY_TXN_TYPE_CD_REDEMPTION)) { // 减
				log.info("会员Id :" + mbrId);
				log.info("会员帐号  ：" + accountId);
				log.info("操作积分  ：" + transPointValue);
				log.info("修改人 ：" + updateBy);

				Map inMap = new HashMap();
				inMap.put("mbrId", mbrId);
				List<Map<String, Object>> dataList = SqlUtil.getInstance().selectList("queryUseredPointBalanceByMbrId",
						inMap);

				/************ 扣减积分余额 ***********/
				cutPointByBalance(sqlMapperList, dataList, transPointValue);// 根据积分余额扣减积分
			} else if (adjustReasonCode.equals(ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL)) { // 加

				/************ 添加积分余额 ***********/
				addPointByBalance(sqlMapperList, mbrId, transPointValue, accountId, updateBy, date);
			}
		} catch (Exception e) {
			log.error("add or cut balance error", e);
			throw new SystemException(this, ErrorBook.OTHER_ERROR, "operatePointBalance失败");
		}
	}

	// 添加积分
	private void addPointByBalance(List<SqlMapper> sqlMapperList, Long mbrId, Long transPointValue, Long accountId,
			String updateBy, Date expiryDate) throws ExceptionAbstract {
		log.info("addPointByBalance begin()...");
		log.info("会员Id :" + mbrId);
		log.info("会员帐号  ：" + accountId);
		log.info("操作积分  ：" + transPointValue);
		log.info("修改人 ：" + updateBy);
		AssertUtils.assertNull(expiryDate);
		Integer result = 0;
		Long pointBalance = 0L;
		SqlMapper sqlMapper = null;
		Map<String, Object> pointBalanceMap = isHasPointBalanceOnMonth(mbrId, expiryDate);// 积分余额记录
		if (pointBalanceMap != null) {
			log.info("存在当月积分余额 [id:" + pointBalanceMap.get("pointBalanceId") + "] 当前积分 :"
					+ pointBalanceMap.get("pointBalance") + "  createMonth: " + pointBalanceMap.get("createMonth"));
			pointBalanceMap.put("updateBy", updateBy);
			pointBalance = CommonUtils.objectToLong(pointBalanceMap.get("pointBalance"), -1L);
			pointBalanceMap.put("pointBalance", pointBalance + transPointValue);
			log.info("更新当月积分 id:" + pointBalanceMap.get("pointBalanceId") + " 更新后积分:"
					+ pointBalanceMap.get("pointBalance"));
			sqlMapper = SqlMapper.getInstance().sqlId("updatePointBalanceByBalanceId").sqlType(TransacOperation.UPDATE)
					.paramMap(pointBalanceMap).build();
			sqlMapperList.add(sqlMapper);
		} else {
			log.info("不存在当月积分余额 则新增...");
			pointBalanceMap = new HashMap<String, Object>();
			pointBalanceMap.put("accountId", accountId);
			pointBalanceMap.put("pointBalance", transPointValue);
			pointBalanceMap.put("stus", ConstantArgs.POINT_BALANCE_STUS_USERLESS);
			pointBalanceMap.put("createBy", updateBy);
			pointBalanceMap.put("expirydate", CommonUtils.format(expiryDate, "yyyy-MM-dd"));
			pointBalanceMap.put("mbrId", mbrId);
			log.info("新增一条本月积分余额   新增后积分:" + pointBalanceMap.get("pointBalance"));
			sqlMapper = SqlMapper.getInstance().sqlId("insertPointBalance").sqlType(TransacOperation.INSERT)
					.paramMap(pointBalanceMap).build();
			sqlMapperList.add(sqlMapper);
		}
	}

	// 是否有积分余额记录
	private Map<String, Object> isHasPointBalanceOnMonth(Long mbrId, Date expirydate) throws ExceptionAbstract {
		if (expirydate == null)
			throw new NullPointerException("isHasNodeOnMonth expirydate is null!");

		Map inMap = new HashMap();
		inMap.put("mbrId", mbrId);
		List<Map<String, Object>> pointBalanceMap = SqlUtil.getInstance().selectList("queryPointBalanceOnMonthByMbrId",
				inMap);
		if (null == pointBalanceMap || pointBalanceMap.size() <= 0) {
			return null;
		}

		Calendar c1 = Calendar.getInstance();
		Calendar c2 = Calendar.getInstance();
		for (Map<String, Object> tempPointBalanceMap : pointBalanceMap) {
			if (CommonUtils.isEmpty(tempPointBalanceMap)) {
				return null;
			} else {
				c1.setTime((Timestamp) tempPointBalanceMap.get("expirydate"));
				c2.setTime(expirydate);
				if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
						&& c1.get(Calendar.DATE) == c2.get(Calendar.DATE))
					return tempPointBalanceMap;
			}
		}
		return null;
	}

	// 根据积分余额扣减积分
	private void cutPointByBalance(List<SqlMapper> sqlMapperList, List<Map<String, Object>> dataList,
			Long transPointValue) throws ExceptionAbstract {
		log.info("cutPointByBalance begin()...");
		log.info("dataList: " + dataList);
		log.info("transPointValue: " + transPointValue);
		int successCount = 0;
		int count = 0;
		Map<String, Object> pointBalanceMap = null;
		SqlMapper sqlMapper = null;
		for (int i = 0, len = dataList.size(); i < len; i++) {
			pointBalanceMap = dataList.get(i);
			Long currentPoint = CommonUtils.objectToLong(pointBalanceMap.get("pointBalance"), -1L);
			if (currentPoint.intValue() == 0) {
				log.info("当前积分为0,跳过操作");
				continue;
			}
			if (CommonUtils.comparison(currentPoint.toString(), transPointValue.toString()) < 0) {// 当前积分小于要扣除的积分时,给当前积分置0
				transPointValue = transPointValue - currentPoint;
				pointBalanceMap.put("pointBalance", 0L);
				sqlMapper = SqlMapper.getInstance().sqlId("updatePointBalanceByBalanceId")
						.sqlType(TransacOperation.UPDATE).paramMap(pointBalanceMap).build();
				sqlMapperList.add(sqlMapper);
				log.info("扣减 帐号余额表  第" + (i + 1) + "笔积分 id:[" + pointBalanceMap.get("pointBalanceId") + "] 当前积分:"
						+ currentPoint + " 扣完积分： 0");
			} else {// 开始扣减
				Long lastPoint = currentPoint - transPointValue;// 剩余积分
				pointBalanceMap.put("pointBalance", lastPoint);
				sqlMapper = SqlMapper.getInstance().sqlId("updatePointBalanceByBalanceId")
						.sqlType(TransacOperation.UPDATE).paramMap(pointBalanceMap).build();
				sqlMapperList.add(sqlMapper);
				transPointValue = transPointValue - currentPoint;// ????? TODO
				log.info("扣减 帐号余额表  第" + (i + 1) + "比积分 id:[" + pointBalanceMap.get("pointBalanceId") + "]  当前积分:"
						+ currentPoint + " 扣完积分： " + lastPoint);
			}
			if (transPointValue <= 0) {
				break;
			}
		}
	}

	/**
	 * 进行积分加减操作的方法
	 * 
	 * @param pointTotal
	 *            总积分
	 * @param updateBy
	 *            更新人员
	 * @param mbrId
	 *            会员Id
	 * @param transType
	 *            扣减类型
	 * @param transPointValue
	 *            积分变更值
	 * @param adjustReasonCode
	 *            积分调节状态码
	 * @param isCrm
	 *            是否集团
	 * @return
	 * @throws ExceptionAbstract
	 * @throws PointServiceException
	 */
	@SuppressWarnings("unused")
	private void addAndCutPoint(List<SqlMapper> sqlMapperList, Long pointTotal, String updateBy, Long mbrId,
			String transType, Long transPointValue, String adjustReasonCode, boolean isCrm) throws ExceptionAbstract {
		log.info("PointCommonFactory addAndCutPoint begin()...");
		log.info("pointTotal: " + pointTotal + ",mbrId: " + ",transPointValue: " + transPointValue);
		log.info("isCrm: " + isCrm + ",adjustReasonCode: " + adjustReasonCode);
		AssertUtils.assertNull(new Object[] { pointTotal, mbrId, transPointValue, adjustReasonCode },
				"mbrId,pointTotal,transPointValue,adjustReasonCode其中不能有空值");
		Integer result = 0;
		Long targePoint = pointTotal;
		Map<String, Object> pointAccountMap = new HashMap<String, Object>();
		SqlMapper sqlMapper = null;
		if (CommonUtils.isNotBlank(pointTotal.toString()) && CommonUtils.isNotBlank(transPointValue.toString())) {
			if (adjustReasonCode.equals(ConstantArgs.LOY_TXN_TYPE_CD_REDEMPTION)) {// 进行积分扣减
				if (isCrm) {// 集团
					targePoint = pointTotal - transPointValue;
				} else {
					if (CommonUtils.comparison(targePoint.toString(), transPointValue.toString()) >= 0) {// 如果总积分大于变更积分,就执行扣减
						targePoint = pointTotal - transPointValue;
					} else {// 否则就提示积分不足
						throw new BusinessException(this, ErrorConstant.Point.ERROR_CUT_POINTS_LESS_THAN_ZERO_20008,
								"积分不足");
					}
				}
			} else if (adjustReasonCode.equals(ConstantArgs.LOY_TXN_TYPE_CD_ACCRUAL)) {// 增加积分
				targePoint = pointTotal + transPointValue;
			}
		}
		// 如果实际积分总额和操作完的积分总额不等,则说明积分扣减或者增加成功,更新积分账户
		if (CommonUtils.comparison(targePoint.toString(), pointTotal.toString()) != 0) {
			pointAccountMap.put("pointTotal", targePoint);
			pointAccountMap.put("updateBy", updateBy);
			pointAccountMap.put("mbrId", mbrId);
			sqlMapper = SqlMapper.getInstance().sqlId("updatePointAccoount").sqlType(TransacOperation.UPDATE)
					.paramMap(pointAccountMap).build();
			sqlMapperList.add(sqlMapper);
		}
	}
	
	/**
	 * 根据兑换id修改积分兑换流水交易状态
	 * @param headMap
	 * @return
	 */
	public int updatePointConvertTranStusByPointConvertId(EngineBean pb) throws ExceptionAbstract {
		log.info("PointCommFactory updatePointConvertTranStusByPointConvertId begin()...param: " + pb.getHeadMap());
		int row = SqlUtil.getInstance().insertOne("updatePointConvertTranStusByPointConvertId", pb.getHeadMap());
		log.info("row: " + row);
		if(row<=0){
			throw new BusinessException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001, "修改积分兑换交易状态失败");
		}
		return row;
	}
}
