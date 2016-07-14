package com.mangocity.de.mbr.datafactory.thirdparty;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.mangocity.ce.bean.EngineBean;
import com.mangocity.ce.book.ErrorBook;
import com.mangocity.ce.exception.BusinessException;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.exception.IllegalParamException;
import com.mangocity.ce.util.AssertUtils;
import com.mangocity.ce.util.CommonUtils;
import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.de.mbr.util.SqlUtil;

/**
 * 
 * @ClassName: ThirdpartyFactory
 * @Description: (第三方服务)
 * @author Yangjie
 * @date 2015年8月25日 下午6:19:22
 */
public class ThirdpartyFactory {
	private static final Logger log = Logger.getLogger(ThirdpartyFactory.class);

	/**
	 * 微信绑定芒果网账户
	 * @param pb
	 *            
	 * @return 如果绑定成功,则返回true
	 * @throws ExceptionAbstract
	 */
	public Map<String,Object> thirdpartyBinding(EngineBean pb) throws ExceptionAbstract {
		log.info("ThirdpartyFactory thirdpartyBinding begin()... ");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		
		if((Boolean)this.isBindedMango(pb).get("isBinding")){//如果该第三方服务已经被绑定了,则提示已经被绑定,不能再重复绑定
			throw new BusinessException(this, ErrorConstant.Thirdparty.ERROR_OPENID_IS_BINDED, "该openid已经被绑定");
		}
		if((Boolean)this.isBindedMango1(pb).get("isBinding")){//如果该第三方服务已经被绑定了,则提示已经被绑定,不能再重复绑定
			throw new BusinessException(this, ErrorConstant.Thirdparty.ERROR_OPENID_IS_BINDED, "芒果网账户已经被绑定,请使用别的芒果网账户");
		}
		int rows = SqlUtil.getInstance().insertOne("bindThirdpartyAccount", headMap);
		Map<String,Object> bodyMap = new HashMap<String, Object>();
		if(rows <= 0){
			bodyMap.put("isBinding", false);
		}
		bodyMap.put("isBinding", true);
		return bodyMap;
	}
	
	public Map queryReisterByOpenid(EngineBean pb)throws ExceptionAbstract {
		log.info("ThirdpartyFactory queryReisterByOpenid begin()... ");
		Map  outMap = SqlUtil.getInstance().selectOne("queryReisterByOpenid", pb.getHeadMap());
		return outMap;
	}
	/**
	 * 微信是否绑定芒果网账户
	 * @param pb {"openid":"xxx","type":""}
	 * @return 如果微信账号绑定
	 * @throws ExceptionAbstract
	 */
	public Map<String,Object> isBindedMango(EngineBean pb) throws ExceptionAbstract {
		log.info("ThirdpartyFactory isBindedWechat begin()... ");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		/*String openid = opate==1?"":(String) pb.getHead("openid");
		String mbrId = opate==1?pb.getHead("mbrId").toString():"";
		String type = (String) pb.getHead("type");*/
		Map<String,Object> dataMap = SqlUtil.getInstance().selectOne("queryTsIntUserByOpenid", headMap);
		log.info("queryTsIntUserByOpenid: " + dataMap);
		Map<String,Object> bodyMap = new HashMap<String, Object>();
		if(CommonUtils.isEmpty(dataMap)){
			bodyMap.put("isBinding", false);
		}else{
			bodyMap.put("isBinding", true);
		}
		return bodyMap;
	}
	public Map<String,Object> isBindedMango1(EngineBean pb) throws ExceptionAbstract {
		log.info("ThirdpartyFactory isBindedWechat begin()... ");
		AssertUtils.assertNull(pb);
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		/*String openid = opate==1?"":(String) pb.getHead("openid");
		String mbrId = opate==1?pb.getHead("mbrId").toString():"";
		String type = (String) pb.getHead("type");*/
		Map<String,Object> dataMap = SqlUtil.getInstance().selectOne("queryTsIntUserByCsn", headMap);
		log.info("queryTsIntUserByCsn: " + dataMap);
		Map<String,Object> bodyMap = new HashMap<String, Object>();
		if(CommonUtils.isEmpty(dataMap)){
			bodyMap.put("isBinding", false);
		}else{
			bodyMap.put("isBinding", true);
		}
		return bodyMap;
	}

	// 直接绑定芒果网账号
	private Map<String, Object> directBindingAccount(Map<String, Object> headMap)
			throws ExceptionAbstract {
		Map<String, Object> bodyMap = new HashMap<String, Object>();
		Long mbrId = CommonUtils.objectToLong(SqlUtil.getInstance()
				.selectOneString("queryMbrIdByLoginNameAndPassword", headMap),
				-1L);// 这里密码接受md5密文处理
		log.info("mbrId: " + mbrId);
		if (mbrId != -1) {// 如果当前用户存在,则直接绑定
			beginBinding(mbrId, headMap);
			bodyMap.put("status", "SUCCESS");
		} else {
			log.info("用户不存在,不能绑定微信账户");
			bodyMap.put("status", "FAILED");
			return bodyMap;
			// 当前用户不存在,ajax提示前台账户或者密码错误
		}
		return bodyMap;
	}

	//注册后绑定芒果网账号
	@SuppressWarnings("unused")
	private Map<String, Object> bindingAccountAfterRegistered(
			Map<String, Object> headMap) {
		// TODO 注册
		return null;
	}

	//如果没有绑定成功,则抛出异常
	private void beginBinding(Long mbrId, Map<String, Object> headMap)
			throws ExceptionAbstract {
		headMap.put("csn", mbrId);
		headMap.put("type", 11);//微信绑定类型
		int rows = SqlUtil.getInstance().insertOne("bindThirdpartyAccount",
				headMap);
		if (rows <= 0) {
			throw new DatabaseException(this, ErrorBook.OTHER_ERROR,
					"微信绑定芒果网账户失败");
		}
	}

}
