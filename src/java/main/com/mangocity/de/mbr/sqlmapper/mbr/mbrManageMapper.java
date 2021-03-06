package com.mangocity.de.mbr.sqlmapper.mbr;

import java.util.List;
import java.util.Map;

public interface mbrManageMapper {
	
	/**
	* @Description: 管理员登陆
	* @param
	* @return Long    返回类型
	 */
	public List<Map<String,Object>> queryUserByLoginNameAndPassword(Map headMap);
	public List<Map<String,Object>> queryModuleByUserId(Map headMap);
}