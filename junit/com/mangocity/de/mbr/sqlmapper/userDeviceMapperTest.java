package com.mangocity.de.mbr.sqlmapper;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.ce.deploy.ConfigManage;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.util.CommonUtils;
import com.mangocity.ce.util.New;
import com.mangocity.de.mbr.util.SqlUtil;

public class userDeviceMapperTest {

	@Test
	public void testAddUserDevice() throws ExceptionAbstract {
		SqlUtil.getInstance().init();
		Map inMap = new HashMap();
		inMap.put("deviceId", "10101010999");
		inMap.put("userId", "120");
		int row = SqlUtil.getInstance().insertOne("addUserDevice", inMap);
		if(row<=0){
			throw new DatabaseException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001, "新增用户设备信息失败");
		}
		System.out.println(row);
	}
	
	@Test
	public void testUpdateUserDevice() throws ExceptionAbstract {
		System.out.println( new Date());
		System.out.println(CommonUtils.addMinutes(new Date(), -1));
		System.out.println(CommonUtils.formatHourMinSec(CommonUtils.addMinutes(new Date(), -1)));
		/*SqlUtil.getInstance().init();
		Map inMap = new HashMap();
		inMap.put("deviceId", "10101010999");
		inMap.put("userId", "330");
		int row = SqlUtil.getInstance().updateOne("updateUserDevice", inMap);
		if(row<=0){
			throw new DatabaseException(this, ErrorConstant.ERROR_INSERT_DATA_FAIL_10001, "新增用户设备信息失败");
		}
		System.out.println(row);*/
	}
	
	@Test
	public void testQueryUserDeviceByUserId() throws ExceptionAbstract {
		SqlUtil.getInstance().init();
		Map<String, Object> headMap = New.map();
		headMap.put("userId", 1);
		List<Map<String,Object>> resultMap =  SqlUtil.getInstance().selectList("queryUserDeviceByUserId", headMap);
		if(null == resultMap){
			throw new DatabaseException(this, ErrorConstant.UserDevice.ERROR_USER_DEVICE_IS_NOT_EXIST, "用户设备信息不存在");
		}
		System.out.println(resultMap);
	}

	@Test
	public void testAction(){
		ConfigManage.instance();
		System.out.println(ConfigManage.instance().getWebAction("queryUserDeviceByUserId"));
	}
}
