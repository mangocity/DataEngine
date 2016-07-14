package com.mangocity.de.mbr.datafactory.device;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.mangocity.ce.bean.EngineBean;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.util.AssertUtils;
import com.mangocity.ce.util.CommonUtils;
import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.de.mbr.util.SqlUtil;

/**
 * @ClassName: ExpandFactory
 * @Description: (扩展服务(用户设备供APP消息推送))
 * @author Yangjie
 * @date 2015年9月8日 下午15:19:22
 */
public class ExpandFactory {
	private static final Logger log = Logger.getLogger(ExpandFactory.class);

	public int addUserDevice(EngineBean pb) throws ExceptionAbstract {
		log.info("ExpandFactory addUserDevice begin()...");
		AssertUtils.assertNull(pb, "EngineBean can't be null.");
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		int row = SqlUtil.getInstance().insertOne("addUserDevice", headMap);
		if(row<=0){
			throw new DatabaseException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001, "新增用户设备信息失败");
		}
		return row;
	}
	
	public int updateUserDevice(EngineBean pb) throws ExceptionAbstract {
		log.info("ExpandFactory updateUserDevice begin()...");
		AssertUtils.assertNull(pb, "EngineBean can't be null.");
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		int row = SqlUtil.getInstance().updateOne("updateUserDevice", headMap);
		if(row<=0){
			throw new DatabaseException(this, ErrorConstant.ERROR_UPDATE_DATA_FAIL_10002, "修改用户设备信息失败");
		}
		return row;
	}
	
	/**
	 * @Title: queryUserDeviceByUserId
	 * @Description: 根据userId查询用户设备信息
	 * @param pb
	 * @param @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 */
	public List<Map<String,Object>> queryUserDeviceByUserId(EngineBean pb) throws ExceptionAbstract {
		log.info("ExpandFactory queryUserDeviceByUserId begin()...");
		AssertUtils.assertNull(pb, "EngineBean can't be null.");
		Map<String, Object> headMap = pb.getHeadMap();
		log.info("headMap: " + headMap);
		List<Map<String,Object>> resultList =  SqlUtil.getInstance().selectList("queryUserDeviceByUserId", headMap);
		if(null == resultList){
			throw new DatabaseException(this, ErrorConstant.UserDevice.ERROR_USER_DEVICE_IS_NOT_EXIST, "用户设备信息不存在");
		}
		return resultList;
	}
}
