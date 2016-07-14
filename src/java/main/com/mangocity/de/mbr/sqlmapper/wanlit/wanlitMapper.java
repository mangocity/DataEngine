package com.mangocity.de.mbr.sqlmapper.wanlit;

import java.util.List;
import java.util.Map;

/**
 * 万里通
 * @author mbr.yangjie
 */
public interface wanlitMapper {
	/**
	* @Title: isExistedPartnerSalesTransNO 
	* @Description: 万里通充值流水是否存在
	* @param 
	* @return List<Map<String,Object>>    返回类型
	 */
	public long isExistedPartnerSalesTransNO(Map headMap);
}