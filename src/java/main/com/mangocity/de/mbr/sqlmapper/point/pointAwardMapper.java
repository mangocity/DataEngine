package com.mangocity.de.mbr.sqlmapper.point;

import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface pointAwardMapper {
	/**
	 * pointAwardCreate
	 * @Description: (新增积分奖励)
	 * @param  headMap 参数说明
	 * @return int 
	 */
	public int pointAwardCreate(Map headMap);
	
	/**
	 * queryAwardCountByOrderNum
	 * @Description: (根据订单号查询积分奖励的记录数)
	 * @param  headMap 参数说明
	 * @return int 
	 */
	public long queryAwardCountByOrderNum(Map headMap);
}