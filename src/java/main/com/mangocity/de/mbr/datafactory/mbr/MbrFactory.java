package com.mangocity.de.mbr.datafactory.mbr;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.mangocity.ce.bean.EngineBean;
import com.mangocity.ce.exception.BusinessException;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.exception.SystemException;
import com.mangocity.ce.util.AssertUtils;
import com.mangocity.ce.util.CommonUtils;
import com.mangocity.ce.util.MD5Algorithm;
import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.de.mbr.book.SqlMapper;
import com.mangocity.de.mbr.book.SqlMapper.TransacOperation;
import com.mangocity.ce.book.ConstantArgs;
import com.mangocity.de.mbr.util.MbrCdUtil;
import com.mangocity.de.mbr.util.SqlUtil;

/**
 * 
 * @ClassName: MbrFactory
 * @Description: TODO(会员数据工厂)
 * @author Syungen
 * @date 2015年8月25日 下午6:19:22
 *
 */
public class MbrFactory {
	private static final Logger log = Logger.getLogger(MbrFactory.class);

	/**
	 * @Title: MbrRegister
	 * @Description: TODO(注册会员)
	 * @param  pb
	 * @param @return
	 * @param @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 * @throws ParseException 
	 * @throws NumberFormatException 
	 */
	public Map mbrRegister(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrFactory mbrRegister begin()...");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		String mobileNo = (String)headMap.get("mobileNo");
		String emailAddr = (String)headMap.get("emailAddr");
		headMap.put("loginName",CommonUtils.isNotBlank(mobileNo)?mobileNo:emailAddr);
		Long recordNo = (Long) SqlUtil.getInstance().selectOneString("validateUniqueMbrByLoginName", headMap);
		if(null != recordNo && recordNo.intValue()>0){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_EXIST_30002, "该登陆名已经注册");
		}
		setProperties(headMap);//设置属性
		log.info("headMap: " + headMap);
		List<Map<String, Object>> registerMapList = new ArrayList<Map<String, Object>>();
		Map<String, Object> mbrshipMap = new HashMap<String, Object>();
		Map<String, Object> mbrMap = new HashMap<String, Object>();
		String mbrshipCd = (String) headMap.get("mbrshipCd");//会籍
		log.info("mbrshipCd: " + mbrshipCd);
		// 如果集团回传会员id和回传客户id不为空,则使用该id
		if (CommonUtils.isNotBlank((String) headMap.get("crmMbrId"))
				&& CommonUtils.isNotBlank((String) headMap.get("crmCustId"))) {
			registerMapList = registerMbrList(Long.valueOf((String)headMap.get("mbrId")),
					headMap, (String) headMap.get("crmMbrshipCd"));
			mbrshipMap = creatCrmMbrshipObject(Long.valueOf((String)headMap.get("mbrId")),
					headMap, (String) headMap.get("crmMbrshipCd"));
			mbrMap = createCrmMbrObject(Long.valueOf((String)headMap.get("mbrId")),
					Long.valueOf((String) headMap.get("personId")), headMap,
					(String) headMap.get("crmMbrshipCd"));
		}else{
			registerMapList = registerMbrList((Long)(headMap.get("mbrId")),//Long.valueOf((String)headMap.get("mbrId"))
					headMap, mbrshipCd);
			mbrshipMap = createMbrShipObject(((Long)(headMap.get("mbrId"))),//Long.valueOf((String)headMap.get("mbrId"))
					headMap, mbrshipCd);
			mbrMap = createMbrObject((Long)(headMap.get("mbrId")), (Long)(headMap.get("personId")),headMap, mbrshipCd);//Long.valueOf((String)headMap.get("mbrId"))
		}
		
		if(CommonUtils.isNotBlank((String)headMap.get("proxyCd"))){
			mbrshipMap.put("agentCode", headMap.get("proxyCd"));
		}
		//获取Person
		Map<String,Object> personMap = createPersonObject((Long)(headMap.get("personId")),headMap);
		//获取积分账户
		Map<String,Object> pointAccountMap = createPointAccountObject((Long)(headMap.get("mbrId")));
		pointAccountMap.put("createBy", headMap.get("createBy"));
		//获取现金账户
		Map<String,Object> cashAccountMap = createCashAccountObject((Long)(headMap.get("mbrId")));
		cashAccountMap.put("createBy", headMap.get("createBy"));
		
		//开始创建账户信息
		createRegisterInfo(mbrshipMap,mbrMap,cashAccountMap,pointAccountMap,personMap,registerMapList);
		
		headMap.put("src", personMap.get("src"));
		headMap.put("addrTyp", personMap.get("addrTyp"));
		headMap.put("crmMbrshipCd", mbrMap.get("crmMbrshipCd"));
		headMap.put("stus", 1);
		headMap.put("mbrId", mbrMap.get("mbrId"));
		headMap.put("mbrCd", mbrMap.get("mbrCd"));
		headMap.put("mbrshipCd", mbrshipMap.get("mbrshipCd"));
		headMap.put("oldMbrshipCd", mbrshipMap.get("oldMbrshipCd"));
		headMap.put("nameCn", personMap.get("nameCn"));
		headMap.put("nameEn",  personMap.get("nameEn"));
		headMap.put("returnTyp", String.valueOf(headMap.get("personId")));
		headMap.put("mbrLevel", mbrMap.get("mbrLevel"));
		headMap.remove("status");//TODO 暂时这样处理
		headMap.remove("registerWay");
		headMap.remove("setPasswordType");
		headMap.remove("signCode");
		headMap.remove("verifyCode");
		//TODO发送邮件
		return headMap;
	}
	
	/**
	 * @Title: login
	 * @Description: 会员登录
	 * @param  pb
	 * @param 
	 * @return Map 返回类型
	 */
	public List<Map<String,Object>> login(EngineBean pb) throws ExceptionAbstract{
		log.info("MbrFactory login begin()...");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		List<Map<String,Object>> resultList = SqlUtil.getInstance().selectList("validateLogin", headMap);
		log.info("resultList: " + resultList);
		if(null == resultList || resultList.size() <= 0){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_USERNAME_OR_PASSWORD_ERROR_30013, "账号或密码错误");
		}
		return resultList;
	}
	
	/**
	 * @Title: resetPassword
	 * @Description: 重置密码/修改密码
	 * @param  pb
	 * @param 
	 * @return Map 返回类型
	 */
	public Long resetPassword(EngineBean pb) throws ExceptionAbstract{
		log.info("MbrFactory resetPassword begin()...");
		AssertUtils.assertNull(pb);
		log.info("headMap" + pb.getHeadMap());
		Long rows = (long) SqlUtil.getInstance().updateOne("resetPassword", pb.getHeadMap());
		if(CommonUtils.isNotEmpty(rows) && rows.intValue()<=0){
			throw new DatabaseException(this, ErrorConstant.ERROR_UPDATE_DATA_FAIL_10002, "重置密码失败");
		}
		log.info("更新行数: " + rows);
		return rows;
	}
	
	/**
	 * @Title: validateUniqueMbrByLoginName
	 * @Description: 校验loginName是否唯一
	 * @param  pb
	 * @param @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 */
	public Map<String,Object> validateUniqueMbrByLoginName(EngineBean pb) throws ExceptionAbstract{
		log.info("MbrFactory validateUniqueMbrByLoginName begin()...");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		String loginName = (String)headMap.get("loginName");
		if(CommonUtils.isBlank(loginName)){
			throw new BusinessException(this, ErrorConstant.ERROR_PARAM_NULL_10000, "loginName must not be null.");
		}
		Long rows = (Long) SqlUtil.getInstance().selectOneString("validateUniqueMbrByLoginName", headMap);
		log.info("validateUniqueMbrByLoginName: " + rows);
		Map<String,Object> bodyMap = new HashMap<String, Object>();
		if(null != rows && rows>0){
			bodyMap.put("isExisted", true);
		}else{
			bodyMap.put("isExisted", false);
		}
		return bodyMap;
	}
	
	/**
	 * @Title: queryMbrIdByLoginName
	 * @Description: 根据登陆名查询会员id
	 * @param  pb
	 * @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 */
	public Map<String,Object> queryMbrIdByLoginName(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrFactory queryMbrIdByLoginName begin()...");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		List<Map<String,Object>> mbrList = SqlUtil.getInstance().selectList("queryMbrIdByLoginName", headMap);
		if(CommonUtils.isEmpty(mbrList)){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "登陆名或密码错误");
		}else if(mbrList.size()>1){
			for(Map<String,Object> map : mbrList){
				Long status = CommonUtils.objectToLong(map.get("status"),-1L);
				if(status == 1L || status == 2L){
					return map;
				}
			}
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "存在多条该登陆名账户,且状态都不能正常使用");
		}else if(mbrList.size() == 1){
			Long status = CommonUtils.objectToLong(mbrList.get(0).get("status"),-1L);
			if(status.intValue() == 0){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_LOGOUT_30005, "该会员账户已注销");
			}else if(status.intValue() == 2){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_FREEZE_30003, "该会员账户已冻结");
			}else if(status.intValue() == 3){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_CONFLICT_30004, "该会员账户状态冲突");
			}else if(status.intValue() != 1){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_CONFLICT_30004, "无法解析该会员状态");
			}
			return mbrList.get(0);
		}
		return null;
	}
	
	/**
	 * @Title: queryMbrIdByLoginNameAndPassword
	 * @Description: 根据登陆名和密码查询会员id
	 * @param  pb
	 * @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 */
	public Map<String,Object> queryMbrIdByLoginNameAndPassword(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrFactory queryMbrIdByLoginNameAndPassword begin()...");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		Map<String,Object> mbrMap = SqlUtil.getInstance().selectOne("queryMbrIdByLoginNameAndPassword", headMap);
		if(CommonUtils.isEmpty(mbrMap)){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "登陆名或密码错误");
		}
		return mbrMap;
	}
	
	/**
	 * @Title: queryRegisterByLoginNameAndPassword
	 * @Description: 根据登陆名和密码查询会员注册信息
	 * @param  pb
	 * @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 */
	public Map<String,Object> queryRegisterByLoginNameAndPassword(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrFactory queryRegisterByLoginNameAndPassword begin()...");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		List<Map<String,Object>> mbrList = SqlUtil.getInstance().selectList("queryRegisterByLoginNameAndPassword", headMap);
		if(CommonUtils.isEmpty(mbrList)){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "登陆名或密码错误");
		}else if(mbrList.size()>1){
			for(Map<String,Object> map : mbrList){
				Long status = CommonUtils.objectToLong(map.get("status"),-1L);
				if(status == 1L || status == 2L){
					return map;
				}
			}
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "存在多条该登陆名账户,且状态都不能正常使用");
		}else if(mbrList.size() == 1){
			Long status = CommonUtils.objectToLong(mbrList.get(0).get("status"),-1L);
			if(status.intValue() == 0){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_LOGOUT_30005, "该会员账户已注销");
			}else if(status.intValue() == 2){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_FREEZE_30003, "该会员账户已冻结");
			}else if(status.intValue() == 3){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_CONFLICT_30004, "该会员账户状态冲突");
			}else if(status.intValue() != 1){
				throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_CONFLICT_30004, "无法解析该会员状态");
			}
			return mbrList.get(0);
		}
		return null;
	}
	
	/**
	 * @Title: updateMobileNo
	 * @Description: 修改手机号码
	 * @param  pb
	 * @param @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 */
	public Map<String,Object> updateMobileNo(EngineBean pb) throws ExceptionAbstract{
		log.info("MbrFactory updateMobileNo begin()...");
		AssertUtils.assertNull(pb);
		Map<String,Object> paramMap = pb.getHeadMap();
		SqlMapper sqlMapper = null;
		List<SqlMapper> sqlMapperList = new ArrayList<SqlMapper>();//事务处理列表*/		
		//先验证新的手机号是否被绑定了
		paramMap.put("loginName", paramMap.get("newMobile"));
		Long count = (Long)SqlUtil.getInstance().selectOneString("validateUniqueMbrByLoginName", paramMap);
		if(count.intValue()>0){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_IS_EXIST_30002, "新手机号已经被注册");
		}
		//1.根据登陆名和stus,查询register列表
		paramMap.put("loginName", paramMap.get("oldMobile"));
		paramMap.put("stus", 1);
		paramMap.put("loginSubType", "M");
		List<Map<String, Object>> registerList = SqlUtil.getInstance().selectList("queryRegisterByLoginNameAndPassword", pb.getHeadMap());
		log.info("根据loginName: " + paramMap.get("loginName") + ",stus=1,loginSubType=M查询注册信息. registerList: " + registerList);
		if(CommonUtils.isEmpty(registerList) || registerList.size()<=0){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "不存在旧手机号对应的有效会员");
		}
		if(registerList.size()>1){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_DUPLICATE_RECORD_30006, "存在多条相同登陆名和loginSubType的会员记录");
		}
		Long mbrId = CommonUtils.objectToLong(registerList.get(0).get("mbrId"), -1L);
		String loginPwd =(String) registerList.get(0).get("loginPwd");
		log.info("根据旧手机号: " + paramMap.get("oldMobile") + " ,查询到的mbrId是: " + mbrId + " ,loginPwd是: " + loginPwd);
		if(mbrId == -1L){
			throw new BusinessException(this, ErrorConstant.Mbr.ERROR_MBR_NOT_EXIST_30001, "该会员无效mbrId: " + mbrId);
		}
		
		//2.更新注册表
		paramMap.put("stus", 0);
		paramMap.put("mbrId", mbrId.intValue());
		paramMap.put("updateBy","member-api");
		paramMap.put("loginSubType","M");
		
		sqlMapper = SqlMapper.getInstance().sqlId("updateRegister").sqlType(TransacOperation.UPDATE).paramMap(paramMap).build();
		sqlMapperList.add(sqlMapper);
		
		//3.更新person表手机号信息
		String mobileNo = (String) paramMap.get("newMobile");
		Map<String,Object> personMap = new HashMap<String,Object>(paramMap);
		personMap.put("mobileNo", mobileNo);
		personMap.put("mobileNo86", "86"+mobileNo);
		log.info("更新person表参数: " + personMap);
		
		sqlMapper = SqlMapper.getInstance().sqlId("updatePersonMobileByMbrId").sqlType(TransacOperation.UPDATE).paramMap(personMap).build();
		sqlMapperList.add(sqlMapper);
		
		//4.插入注册表信息
		Map<String,Object> registerMap = new HashMap<String,Object>(paramMap);
		registerMap.put("loginName", mobileNo);
		registerMap.put("loginPwd", loginPwd);
		registerMap.put("loginType", 1);
		registerMap.put("createBy", "member-api");
		log.info("插入注册表参数: " + registerMap);
		
		sqlMapper = SqlMapper.getInstance().sqlId("registerCreate").sqlType(TransacOperation.INSERT).paramMap(registerMap).build();
		sqlMapperList.add(sqlMapper);
		
		//5.添加更新日志
		sqlMapper = SqlMapper.getInstance().sqlId("mbrUpdateLogCreate").sqlType(TransacOperation.INSERT).paramMap(registerMap).build();
		sqlMapperList.add(sqlMapper);
		
		SqlUtil.getInstance().transactionAll(sqlMapperList);
		Map<String,Object> bodyMap = new HashMap<String, Object>();
		bodyMap.put("isUpdated", true);
		return bodyMap;
	}
	
	//设置属性
	private void setProperties(Map<String, Object> headMap) throws ExceptionAbstract {
		AssertUtils.assertNull(headMap);
		//生成主键信息
		Long mbrId = (Long) SqlUtil.getInstance().selectOneString("getMbrId", null);
		Long personId = (Long) SqlUtil.getInstance().selectOneString("getPersonId", null);
		Long mbrshipId = (Long) SqlUtil.getInstance().selectOneString("getMbrshipId", null);
		String mbrshipCd =  MbrCdUtil.getMbrshipCd(String.valueOf(mbrshipId)).toString();
		log.info("生成主键信息: mbrId: " + mbrId +",personId: " + personId + ",mbrshipId: " + mbrshipId);
		headMap.put("mbrId",mbrId);
		headMap.put("personId",personId);
		headMap.put("mbrshipId",mbrshipId);
		headMap.put("mbrshipCd",mbrshipCd);
		
		//设置普通信息
		headMap.put("createBy", CommonUtils.format(new Date(), "yyyy-MM-dd"));
		headMap.put("updateBy", CommonUtils.format(new Date(), "yyyy-MM-dd"));
		
		
	}
	
	//开始创建账户信息
	private void createRegisterInfo(Map<String, Object> mbrshipMap,
			Map<String, Object> mbrMap, Map<String, Object> cashAccountMap,
			Map<String, Object> pointAccountMap, Map<String, Object> personMap,
			List<Map<String, Object>> registerMapList) throws ExceptionAbstract {
		log.info("createRegisterInfo begin()...");
		if (null == mbrshipMap || null == mbrMap || null == cashAccountMap
				|| null == pointAccountMap || null == personMap
				|| null == registerMapList) {
			log.error("创建对象有空值");
			throw new BusinessException(this,ErrorConstant.ERROR_PARAM_NULL_10000,"参数不能为空");
		}
		log.info("插入t_mbr_register信息，registerMapList="+registerMapList);
		registerListCreate(registerMapList);
		
		log.info("插入t_mbr_person信息，personMap="+personMap);
		personCreate(personMap);
		
		log.info("插入t_mbr_mbrship信息，mbrshipMap="+mbrshipMap);
		mbrshipCreate(mbrshipMap);
		
		log.info("插入t_mbr信息，mbrMap="+mbrMap);
		mbrCreate(mbrMap);
		
		log.info("插入PointAccount信息，pointAccountMap="+pointAccountMap);
		addPointAccount(pointAccountMap);
		
		log.info("插入CashAccount信息，cashAccountMap="+cashAccountMap);
		cashAccountCreate(cashAccountMap);
	}

	//插入t_mbr_cash_account_sec信息
	private void cashAccountCreate(Map<String, Object> cashAccountMap) throws ExceptionAbstract {
		AssertUtils.assertNull(cashAccountMap);
		log.info("cashAccountCreate begin()...");
		log.info("cashAccountMap: " + cashAccountMap);
		int i = SqlUtil.getInstance().insertOne("cashAccountCreate", cashAccountMap);
		if(i<=0){
			log.info("现金账户创建失败");
			throw new DatabaseException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001,"现金账户创建失败");
		}
	}

	//插入T_MBR_POINT_ACCOUNT信息
	private void addPointAccount(Map<String, Object> pointAccountMap) throws ExceptionAbstract {
		AssertUtils.assertNull(pointAccountMap);
		log.info("addPointAccount begin()...");
		log.info("pointAccountMap: " + pointAccountMap);
		int i = SqlUtil.getInstance().insertOne("pointAccountCreate", pointAccountMap);
		if(i<=0){
			log.info("积分账户创建失败");
			throw new DatabaseException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001,"积分账户创建失败");
		}
	}

	//插入t_mbr信息
	private void mbrCreate(Map<String, Object> mbrMap) throws ExceptionAbstract {
		AssertUtils.assertNull(mbrMap);
		log.info("mbrCreate begin()...");
		log.info("mbrMap: " + mbrMap);
		int i = SqlUtil.getInstance().insertOne("mbrCreate", mbrMap);
		if(i<=0){
			log.info("会员账户创建失败");
			throw new DatabaseException(this,ErrorConstant.ERROR_INSERT_DATA_FAIL_10001,"会员账户创建失败");
		}
	}

	//插入t_mbr_mbrship信息
	private void mbrshipCreate(Map<String, Object> mbrshipMap) throws ExceptionAbstract {
		AssertUtils.assertNull(mbrshipMap);
		log.info("mbrshipCreate begin()...");
		log.info("mbrshipMap: " + mbrshipMap);
		int i = SqlUtil.getInstance().insertOne("mbrShipCreate", mbrshipMap);
		if(i<=0){
			log.info("会籍账户创建失败");
			throw new DatabaseException(this,ErrorConstant.ERROR_INSERT_DATA_FAIL_10001,"会籍创建失败");
		}
	}

	//插入t_mbr_person信息
	private void personCreate(Map<String, Object> personMap) throws ExceptionAbstract {
		AssertUtils.assertNull(personMap);
		log.info("personCreate begin()...");
		log.info("personMap: " + personMap);
		int i = SqlUtil.getInstance().insertOne("personCreate", personMap);
		if(i<=0){
			log.info("自然人账户创建失败");
			throw new DatabaseException(this,ErrorConstant.ERROR_INSERT_DATA_FAIL_10001,"自然人创建失败");
		}
	}

	//插入t_mbr_register信息
	private void registerListCreate(List<Map<String, Object>> registerMapList) throws ExceptionAbstract {
		AssertUtils.assertNull(registerMapList);
		log.info("registerListCreate begin()...");
		log.info("registerMapList: " + registerMapList);
		String loginPwd = null;
		for(int i=0,len = registerMapList.size();i<len;i++){
			loginPwd = (String)registerMapList.get(i).get("loginPwd");
			if(CommonUtils.isNotBlank(loginPwd)){
				if(loginPwd.length() != 32){//非Md5加密
					registerMapList.get(i).put("loginPwd", new MD5Algorithm().generateMD5Str(loginPwd));
				}else{
					registerMapList.get(i).put("loginPwd", loginPwd);
				}
			}
			//插入记录
			SqlUtil.getInstance().insertOne("registerCreate", registerMapList.get(i));
		}
	}

	//创建现金账户对象
	private Map<String, Object> createCashAccountObject(Long mbrId) throws ExceptionAbstract {
		log.info("createCashAccountObject begin()...");
		log.info("mbrId: " + mbrId);
		Map<String, Object> cashAccountMap = new HashMap<String, Object>();
		cashAccountMap.put("mbrId", mbrId);
		cashAccountMap.put("cashTotal", 0D);
		cashAccountMap.put("version", 1);
		cashAccountMap.put("status", "EFFECTIVE");
		cashAccountMap.put("cashAccountId", SqlUtil.getInstance().selectOneString("generateCashAccountId", null));
		return cashAccountMap;
	}

	//创建积分账户对象
	private Map<String, Object> createPointAccountObject(Long mbrId) {
		log.info("createPointAccountObject begin()...");
		log.info("mbrId: " + mbrId);
		Map<String, Object> pointAccountMap = new HashMap<String, Object>();
		pointAccountMap.put("mbrId", mbrId);
		pointAccountMap.put("pointTotal", 0);
		pointAccountMap.put("version", 1);
		return pointAccountMap;
	}

	//创建Person对象
	private Map<String, Object> createPersonObject(Long personId,
			Map<String, Object> headMap) throws ExceptionAbstract {
		log.info("createPersonObject begin()...");
		log.info("personId: " + personId);
		Map<String, Object> personMap = new HashMap<String, Object>();
		personMap.put("personId", personId);
		personMap.put("familyName", headMap.get("familyName"));
		personMap.put("name", headMap.get("name"));
		String familyName = null == personMap.get("familyName")?"":(String)personMap.get("familyName");
		String firstName = null == personMap.get("firstName")?"":(String)personMap.get("firstName");
		String middleName = null == personMap.get("middleName")?"":(String)personMap.get("middleName");
		String lastName = null == personMap.get("lastName")?"":(String)personMap.get("lastName");
		String name = null == personMap.get("name")?"":(String)personMap.get("name");
		personMap.put("nameCn", familyName+name);
		personMap.put("nameEn", firstName+middleName+lastName);
		personMap.put("nationalityCd", headMap.get("nationalityCd"));
		personMap.put("recommendName", headMap.get("recommendName"));
		personMap.put("gender", headMap.get("gender"));
		personMap.put("phoneNo", headMap.get("phoneNo"));
		personMap.put("ofcPhoneNo", headMap.get("ofcPhoneNo"));
		personMap.put("fmyPhoneNo", headMap.get("fmyPhoneNo"));
		personMap.put("fax", headMap.get("fax"));
		personMap.put("dietHabit", headMap.get("dietHabit"));
		if(CommonUtils.isNotBlank((String)headMap.get("mobileNo"))){
			personMap.put("mobileNo", headMap.get("mobileNo"));
		}
		if(CommonUtils.isNotBlank((String)headMap.get("emailAddr"))){
			personMap.put("emailAddr", headMap.get("emailAddr"));
		}
		personMap.put("msn", headMap.get("msn"));
		personMap.put("perfLangCd", headMap.get("perfLangCd"));
		personMap.put("perfCharacterCd", headMap.get("perfCharacterCd"));
		personMap.put("marriageStusCd", headMap.get("marriageStusCd"));
		personMap.put("interst", headMap.get("interst"));
		personMap.put("promotionWay", headMap.get("promotionWay"));
		personMap.put("certNo", headMap.get("certNo"));
		personMap.put("crmIdcard", headMap.get("crmIdcard"));
		personMap.put("industryCd", headMap.get("industryCd"));
		
		if(CommonUtils.isNotBlank((String)headMap.get("appellation"))){
			personMap.put("appellation", headMap.get("appellation"));
		}
		if(CommonUtils.isNotBlank((String)headMap.get("birthday"))){
			try {
				personMap.put("birthday", CommonUtils.parseDate((String)headMap.get("birthday"), "yyyy-MM-dd"));
			} catch (ParseException e) {
				throw new SystemException(this, ErrorConstant.ERROR_PARAM_PARSE_FAIL_10003, "生日转换失败");
			}
		}
		personMap.put("resideCity", headMap.get("resideCity"));
		//TODO根据手机定位 ejb 733
		
		personMap.put("unitName", headMap.get("unitName"));
		if(CommonUtils.isNotBlank((String)headMap.get("industryStandard"))){
			personMap.put("industryStandard", headMap.get("industryStandard"));
		}
		if(CommonUtils.isNotBlank((String)headMap.get("certTypId"))){
			personMap.put("certTypId", headMap.get("certTypId"));
		}
		if(CommonUtils.isNotBlank((String)headMap.get("birthPlace"))){
			personMap.put("birthPlace", headMap.get("birthPlace"));
		}
		if(CommonUtils.isNotBlank((String)headMap.get("registerSrcId"))){
			personMap.put("registerSrcId", headMap.get("registerSrcId"));
		}
		
		personMap.put("addrStateCd", headMap.get("addrStateCd"));
		personMap.put("addrCityCd", headMap.get("addrCityCd"));
		personMap.put("addrDetl", headMap.get("addrDetl"));
		personMap.put("addrCountyCd", headMap.get("addrCountyCd"));
		personMap.put("ofcCountryCd", headMap.get("ofcCountryCd"));
		personMap.put("fmyCountryCd", headMap.get("fmyCountryCd"));
		personMap.put("ofcDistrictCd", headMap.get("ofcDistrictCd"));
		personMap.put("fmyDistrictCd", headMap.get("fmyDistrictCd"));
		personMap.put("race", headMap.get("race"));
		personMap.put("src", "66");
		personMap.put("addrPost", headMap.get("addrPost"));
		personMap.put("addrTyp","Register Address");
		personMap.put("educationCd", headMap.get("educationCd"));
		personMap.put("mobileCountryCd", headMap.get("mobileCountryCd"));
		personMap.put("countryCd", headMap.get("countryCd"));
		
		if(CommonUtils.isNotBlank((String)headMap.get("mobileCountryCd"))){
			personMap.put("mobile", (String)headMap.get("mobileCountryCd")+(String)headMap.get("mobileNo"));
		}else{
			personMap.put("mobile",headMap.get("mobileNo"));
		}
		
		personMap.put("addrCountryCd",headMap.get("addrCountryCd"));
		personMap.put("proxyCd",headMap.get("proxyCd"));
		personMap.put("position",headMap.get("position"));
		personMap.put("revenueCd",headMap.get("revenueCd"));
		personMap.put("createBy",headMap.get("createBy"));
		personMap.put("updateBy",headMap.get("updateBy"));
		//增加接受推广字段的值
		if(CommonUtils.isNotBlank((String)headMap.get("isAgreeSendPromotion"))){
			personMap.put("isAgreeSendPromotion",headMap.get("isAgreeSendPromotion"));
		}
		return personMap;
	}

	// 创建集团Mbr对象
	private Map<String, Object> createCrmMbrObject(Long mbrId, Long personId,
			Map<String, Object> headMap, String mbrshipNo) throws ExceptionAbstract {
		log.info("createCrmMbrObject begin()...");
		log.info("mbrId: " + mbrId);
		log.info("personId: " + personId);
		log.info("mbrshipNo: " + mbrshipNo);
		Map<String,Object> crmMbrMap = new HashMap<String, Object>();
		String seq12 = SqlUtil.getInstance().selectOneString("getSeq12", null).toString();//获取会员编码的12位seq
		crmMbrMap.put("mbrId", mbrId);
		crmMbrMap.put("mbrCd", MbrCdUtil.getMbrCd("02", seq12));
		crmMbrMap.put("mbrNetName", headMap.get("mbrNetName"));
		crmMbrMap.put("mbrTyp", headMap.get("mbrTyp"));
		crmMbrMap.put("personId",personId);
		crmMbrMap.put("attribute",headMap.get("attribute"));
		
		Map<String, Object> mbrshipMap = createMbrShipObject(mbrId, headMap, mbrshipNo);
		if(CommonUtils.isNotEmpty(mbrshipMap)){
			crmMbrMap.put("crmMbrshipCd", mbrshipMap.get("mbrshipCd"));
			crmMbrMap.put("defaultMbrshipCd", mbrshipMap.get("oldMbrshipCd"));
		}
		if(CommonUtils.isNotBlank((String)headMap.get("mbrLevel"))){
			crmMbrMap.put("mbrLevel", headMap.get("mbrLevel"));
		}else{
			crmMbrMap.put("mbrLevel","One");
		}
		crmMbrMap.put("crmMbrId", headMap.get("crmMbrId"));
		crmMbrMap.put("crmCustId", headMap.get("crmCustId"));
		crmMbrMap.put("createBy", headMap.get("loginName"));
		return crmMbrMap;
	}

	// 创建集团MbrShip对象
	private Map<String, Object> creatCrmMbrshipObject(Long mbrId,
			Map<String, Object> headMap, String crmMbrshipCd) throws ExceptionAbstract {
		log.info("creatCrmMbrshipObject begin()...");
		log.info("mbrId: " + mbrId);
		log.info("crmMbrshipCd: " + crmMbrshipCd);
		Map<String, Object> mbrShipMap = new HashMap<String, Object>();
		if (CommonUtils.isNotBlank((String) headMap.get("mbrshipCategoryId"))) {// 如果会籍类型名不为空,则根据类型查会籍信息
			Map<String, Object> categoryMap = SqlUtil.getInstance().selectOne(
					"queryMbrshipCategoryByCategoryId", headMap);
			log.info("createMbrShipMap categoryMap: " + categoryMap);
			if (CommonUtils.isNotEmpty(categoryMap)) {// 如果会籍信息不为空,则
				mbrShipMap.put("mbrshipCategoryId",
						categoryMap.get("mbrshipCategoryId"));
				mbrShipMap.put("mbrshipCategoryCd",
						categoryMap.get("mbrshipCategoryCd"));
				// 判断是否是推广卡
				if ((int) (categoryMap.get("aliasCardTyp")) == 4) {
					log.info("aliasCardTyp=4: 是推广卡");
					mbrShipMap.put("oldMbrshipCd", crmMbrshipCd);
					mbrShipMap.put("mbrshipCd", crmMbrshipCd);
				}else{//ejb中做了相同处理
					mbrShipMap.put("oldMbrshipCd", crmMbrshipCd);
					mbrShipMap.put("mbrshipCd", crmMbrshipCd);
				}
			} else {
				mbrShipMap.put("oldMbrshipCd", crmMbrshipCd);
				mbrShipMap.put("mbrshipCd", crmMbrshipCd);
			}
		}
		mbrShipMap.put("registerIp", headMap.get("registerIp"));
		mbrShipMap.put("mbrshipId", headMap.get("mbrshipId"));
		mbrShipMap.put("mbrId", mbrId);
		mbrShipMap.put("stus", headMap.get("stus"));
		mbrShipMap.put("projectCode", headMap.get("projectCode"));
		mbrShipMap.put("registerSrcId", headMap.get("registerSrcId"));
		mbrShipMap.put("availableDat", new Date());
		mbrShipMap.put("updateTime", new Date());
		mbrShipMap.put("createBy", headMap.get("createBy"));
		mbrShipMap.put("updateBy", headMap.get("updateBy"));
		return mbrShipMap;
	}

	// 创建Mbr对象
	@SuppressWarnings("unused")
	private Map<String, Object> createMbrObject(Long mbrId, Long personId,
			Map<String, Object> headMap, String mbrshipNo) throws ExceptionAbstract {
		log.info("createMbrObject begin()...");
		log.info("mbrId: " + mbrId);
		log.info("personId: " + personId);
		log.info("mbrshipNo: " + mbrshipNo);
		Map<String,Object> mbrMap = new HashMap<String, Object>();
		String seq12 = SqlUtil.getInstance().selectOneString("getSeq12", null).toString();//获取会员编码的12位seq
		mbrMap.put("mbrId", mbrId);
		mbrMap.put("mbrCd", MbrCdUtil.getMbrCd("02", seq12));
		mbrMap.put("mbrNetName", headMap.get("mbrNetName"));
		mbrMap.put("mbrTyp", null == headMap.get("mbrTyp")?"Individual":headMap.get("mbrTyp"));
		mbrMap.put("personId",personId);
		mbrMap.put("attribute",headMap.get("attribute"));
		
		Map<String, Object> mbrshipMap = createMbrShipObject(mbrId, headMap, mbrshipNo);
		if(CommonUtils.isNotEmpty(mbrshipMap)){
			mbrMap.put("crmMbrshipCd", mbrshipMap.get("mbrshipCd"));
			mbrMap.put("defaultMbrshipCd", mbrshipMap.get("oldMbrshipCd"));
		}
		//设置mbr最低级别
		if(CommonUtils.isNotEmpty(mbrshipMap.get("mbrshipCategoryId"))){
			Map<String, Object> mbrshipCategoryMap = SqlUtil.getInstance().selectOne(
					"queryMbrshipCategoryByCategoryId", mbrshipMap);
			if(CommonUtils.isNotEmpty(mbrshipCategoryMap) && CommonUtils.isNotBlank((String)mbrshipCategoryMap.get("mbrshipCategoryLowestLevel"))){
				mbrMap.put("mbrLowestLevel", mbrshipCategoryMap.get("mbrshipCategoryLowestLevel"));
				mbrMap.put("mbrLevel", mbrshipCategoryMap.get("mbrshipCategoryLowestLevel"));
			}else{
				mbrMap.put("mbrLevel", "One");
			}
		}
		mbrMap.put("createBy", /*headMap.get("loginName")*/"NEW-MEMBER");
		return mbrMap;
	}

	// 设置会籍数据
	@SuppressWarnings("unused")
	private Map<String, Object> createMbrShipObject(Long mbrId,
			Map<String, Object> registerMbrMap, String mbrshipNo)
			throws ExceptionAbstract {
		log.info("createMbrShipObject begin()...");
		log.info("mbrId: " + mbrId);
		log.info("registerMbrMap: " + registerMbrMap);
		log.info("mbrshipNo: " + mbrshipNo);
		Map<String, Object> mbrShipMap = new HashMap<String, Object>();
		Map map = new HashMap();
		String mbrshipCategoryCd = (String)registerMbrMap.get("mbrshipCategoryCd");
		if(CommonUtils.isBlank(mbrshipCategoryCd)){//如果没有传入自己的会籍编码,默认注册为芒果网会籍
			mbrshipCategoryCd = ConstantArgs.DEFULT_MBRSHIP;//默认会籍
			log.info("没有指定mbrshipCategoryCd,使用默认芒果网会籍");
		}
		map.put("categoryCd", mbrshipCategoryCd);
		map = SqlUtil.getInstance().selectOne("queryMbrshipCategoryByCategoryCd", map);
		Map<String, Object> categoryMap = SqlUtil.getInstance().selectOne(
				"queryMbrshipCategoryByCategoryId", map);
		log.info("createMbrShipMap categoryMap: " + categoryMap);
		if (CommonUtils.isNotEmpty(categoryMap)) {// 如果会籍信息不为空,则
			mbrShipMap.put("mbrshipCategoryId",
					categoryMap.get("mbrshipCategoryId"));
			mbrShipMap.put("mbrshipCategoryCd",
					categoryMap.get("mbrshipCategoryCd"));
			// 判断是否是推广卡
			if (CommonUtils.objectToLong(categoryMap.get("aliasCardType"), -1L).intValue() == 4) {
				log.info("aliasCardTyp=4: 是推广卡");
				mbrShipMap.put("oldMbrshipCd", mbrshipNo);
				if (mbrshipNo != null && mbrshipNo.length() < 12) {
					mbrshipNo = "66" + mbrshipNo;
				}
				mbrShipMap.put("mbrshipCd", mbrshipNo);
			}else {
				mbrShipMap.put("oldMbrshipCd", mbrshipNo);
				mbrShipMap.put("mbrshipCd", mbrshipNo);
			}
		} 
		mbrShipMap.put("registerIp", registerMbrMap.get("registerIp"));
		mbrShipMap.put("mbrshipId", registerMbrMap.get("mbrshipId"));
		mbrShipMap.put("mbrId", mbrId);
		mbrShipMap.put("stus", ConstantArgs.DEFAULT_MBRSHIP_STATUS);
		mbrShipMap.put("projectCode", registerMbrMap.get("projectCode"));
		mbrShipMap.put("registerSrcId", registerMbrMap.get("registerSrcId"));
		mbrShipMap.put("createBy", registerMbrMap.get("createBy"));
		mbrShipMap.put("updateBy", registerMbrMap.get("updateBy"));
		return mbrShipMap;
	}

	// 获得需要插入注册表的注册记录
	@SuppressWarnings("unused")
	private List<Map<String, Object>> registerMbrList(Long mbrId,
			Map<String, Object> registerMap, String mbrshipNo)
			throws ExceptionAbstract {
		log.info("registerMbrList begin()...");
		log.info("mbrId: " + mbrId);
		log.info("mbrshipNo: " + mbrshipNo);
		AssertUtils.assertNull(registerMap);
		if (CommonUtils.isEmpty(mbrId) || mbrId.intValue() <= 0) {
			throw new BusinessException(this, ErrorConstant.ERROR_PARAM_NULL_10000,"mbrId不能为空");
		}
		if (CommonUtils.isBlank(mbrshipNo)) {
			throw new BusinessException(this,ErrorConstant.ERROR_PARAM_NULL_10000,"mbrshipNo不能为空");
		}
		List<Map<String, Object>> registerMbrList = new ArrayList<Map<String,Object>>();
		String loginPwd = null;
		String loginName = null;
		if (CommonUtils.isNotBlank((String) registerMap.get("loginPwd"))) {// 如果传入了密码就使用传入密码
			loginPwd = (String) registerMap.get("loginPwd");
		} else {// 否则生成随机密码
			loginPwd = MbrCdUtil.gen6RandomPwd();
			registerMap.put("loginPwd", loginPwd);
		}
		log.info("loginPwd: " + loginPwd);
		// 手机登陆方式
		if (CommonUtils.isNotBlank((String) registerMap.get("mobileNo"))) {
			loginName = (String) registerMap.get("mobileNo");
			Map<String, Object> registerMbrMap = createRegisterMap(mbrId,
					loginName, 1, "M", registerMap);
			registerMbrList.add(registerMbrMap);
		}

		// 邮箱登陆方式
		if (CommonUtils.isNotBlank((String) registerMap.get("emailAddr"))) {
			loginName = (String) registerMap.get("emailAddr");
			Map<String, Object> registerMbrMap = createRegisterMap(mbrId,
					loginName, 1, "E", registerMap);
			registerMbrMap.put("registerType", registerMap.get("isLoginType"));// 区分注册与登陆的验证判断
			registerMbrList.add(registerMbrMap);
		}

		// 网上用户名
		if (CommonUtils.isNotBlank((String) registerMap.get("mbrNetName"))) {
			loginName = (String) registerMap.get("mbrNetName");
			Map<String, Object> registerMbrMap = createRegisterMap(mbrId,
					loginName, 1, "N", registerMap);// 生成注册表中的一条记录
			registerMbrMap.put("registerType", registerMap.get("isLoginType"));// 区分注册与登陆的验证判断
			registerMbrList.add(registerMbrMap);
		}

		// 会籍编号方式 
		Map<String, Object> mbrShipMap = createMbrShipObject(mbrId, registerMap,
				mbrshipNo);// 创建会籍Map
		if (CommonUtils.isNotEmpty(mbrShipMap)) {
			loginName = (String) mbrShipMap.get("oldMbrshipCd");
			Map<String, Object> registerMbrMap = createRegisterMap(mbrId,
					loginName, 1, "C", registerMap);// 生成注册表中的一条记录
			registerMbrMap.put("registerType", registerMap.get("isLoginType"));// 区分注册与登陆的验证判断
			registerMbrList.add(registerMbrMap);
		}

		// 证件类型
		String certNo = (String) registerMap.get("certNo");
		String certTypId = (String) registerMap.get("certTypId");
		if (CommonUtils.isNotBlank(certTypId) && CommonUtils.isNotBlank(certNo)) {
			Map<String, Object> registerMbrMap = createRegisterMap(mbrId,
					certNo, 3, certTypId, registerMap);// 生成注册表中的一条记录
			registerMbrMap.put("registerType", registerMap.get("isLoginType"));// 区分注册与登陆的验证判断
			registerMbrList.add(registerMbrMap);
		}

		// 联名卡
		Map<String, Object> aliasCardMap = createAliasCardMap(registerMap,
				mbrshipNo);// 创建会籍Map
		if (null != aliasCardMap) {
			String mbrshipCategoryCd = (String) aliasCardMap
					.get("mbrshipCategoryCd");
			boolean isEncrypts = isEncrypt(mbrshipCategoryCd);
			String aliasNo = (String) aliasCardMap.get("aliasNo");
			loginName = isEncrypts ? new MD5Algorithm().generateMD5Str(aliasNo)
					: aliasNo;
			Map<String, Object> registerMbrMap = createRegisterMap(mbrId,
					loginName, 2, mbrshipCategoryCd, registerMap);// 生成注册表中的一条记录
			registerMbrMap.put("registerType", registerMap.get("isLoginType"));// 区分注册与登陆的验证判断
			registerMbrList.add(registerMbrMap);
		}
		return registerMbrList;
	}

	// 判断联名卡or银行卡的卡号是否加密
	// return true:加密；false：不加密
	private boolean isEncrypt(String mbrshipCategoryCd)
			throws ExceptionAbstract {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("categoryCd", mbrshipCategoryCd);
		Map<String, Object> mbrShipCategoryMap = SqlUtil.getInstance()
				.selectOne("mbrshipCategoryByCategoryCd", map);
		int aliasCardTyp = (int) mbrShipCategoryMap.get("AliasCardTyp");
		int isEncryptCardNo = (int) mbrShipCategoryMap.get("isEncryptCardNo");
		if (CommonUtils.isNotEmpty(mbrShipCategoryMap)
				&& (aliasCardTyp == 1 || aliasCardTyp == 2)
				&& isEncryptCardNo == 1) {
			return true;
		}
		return false;
	}

	// 创建联名卡Map
	private Map<String, Object> createAliasCardMap(
			Map<String, Object> registerMap, String mbrshipNo)
			throws ExceptionAbstract {
		log.info("createAliasCardMap begin()...");
		log.info("mbrshipNo: " + mbrshipNo);
		if (CommonUtils.isNotEmpty(registerMap.get("categoryName"))) {
			Map<String, Object> categoryMap = SqlUtil.getInstance().selectOne(
					"queryMbrshipCategoryByCategoryId", registerMap);
			if (CommonUtils.isNotEmpty(categoryMap)) {
				// 判断是否是联名卡 mc.getAliasCardTyp() == 2 为卡_bin 不写注册表
				if (((int) categoryMap.get("aliasCardTyp")) == 1
						&& CommonUtils.isNotBlank((String) registerMap
								.get("aliasNo"))) {
					Map<String, Object> aliasCardMap = new HashMap<String, Object>();
					aliasCardMap.put("aliasNo", registerMap.get("aliasNo"));
					aliasCardMap.put("channelNo", categoryMap.get("channelNo"));
					aliasCardMap.put("corporateAccNo", 0);
					aliasCardMap.put("mbrshipCategoryCd",
							categoryMap.get("categoryCd"));
					aliasCardMap.put("mbrshipCd", mbrshipNo);
					// //联名卡号加密前的后4位
					String aliasNo = (String) aliasCardMap.get("aliasNo");
					if (((String) aliasCardMap.get("aliasNo")).length() == 4) {
						aliasCardMap.put(
								"shortAliasMbrCd",
								aliasNo.subSequence(aliasNo.length() - 4,
										aliasNo.length()).toString());
					}
					aliasCardMap.put("createBy", registerMap.get("createBy"));
					aliasCardMap.put("stus", "A");
					return categoryMap;
				}
			}
		}
		return null;
	}

	// 创建注册表Map
	private Map<String, Object> createRegisterMap(Long mbrId, String loginName,
			int loginType, String loginSubType, Map<String, Object> registerMap) {
		log.info("createRegisterMap begin()...");
		log.info("mbrId: " + mbrId);
		log.info("loginName: " + loginName);
		log.info("loginType: " + loginType);
		log.info("loginSubType: " + loginSubType);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("loginName", loginName);
		map.put("registerId", registerMap.get("registerId"));
		map.put("loginType", loginType);
		map.put("loginSubType", loginSubType);
		map.put("loginPwd", registerMap.get("loginPwd"));
		map.put("mbrId", mbrId);
		map.put("createBy", registerMap.get("createBy"));
		map.put("updateBy", registerMap.get("updateBy"));
		return map;
	}

	// 创建registerList
	@SuppressWarnings("unused")
	private void createRegisterList(List<Map<String, Object>> registerList)
			throws ExceptionAbstract {
		log.info("createRegisterList begin()...");
		log.info("registerList: " + registerList);
		AssertUtils.assertNull(registerList);
		Map<String, Object> registerMap = null;
		for (int i = 0, len = registerList.size(); i < len; i++) {
			registerMap = registerList.get(i);
			if (null != registerMap
					&& ((String) registerMap.get("loginPwd")).length() != 32) {// 如果获取的密码是没有加密过的,则进行加密,否则不做处理
				registerMap.put("loginPwd", new MD5Algorithm()
						.generateMD5Str((String) registerMap.get("loginPwd")));
			}
			// 添加会员注册表
			int rowNo = SqlUtil.getInstance().insertOne("registerCreate",
					registerMap);
			if (rowNo <= 0) {
				throw new DatabaseException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001,"注册会员"+registerMap+"失败");
			}
		}
	}
}
