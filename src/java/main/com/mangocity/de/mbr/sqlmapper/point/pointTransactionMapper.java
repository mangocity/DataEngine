package com.mangocity.de.mbr.sqlmapper.point;

import java.util.List;
import java.util.Map;

public interface pointTransactionMapper {
	/**
	* @Title: queryPointTransactionByPagination 
	* @Description: (分页显示积分交易明细) 
	* @param  headMap
	* @return List<Map<String,Object>>    返回类型
	* TODO查询速度极慢
	 */
	public List<Map<String,Object>> queryPointTransactionByPagination(Map headMap);
	
}