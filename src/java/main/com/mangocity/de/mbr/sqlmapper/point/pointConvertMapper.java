package com.mangocity.de.mbr.sqlmapper.point;

import java.util.List;
import java.util.Map;

public interface pointConvertMapper {
	/**
	  * @Title:  按条件查询绑定信息
	  * @param  headMap  参数说明 
	  * @return Map    返回类型
	 */
	public List<Map<String,Object>> queryWltAccoutByCondition(Map<String,Object> headMap);
	
	/**
	  * @Title:  创建万里通绑定记录
	  * @param  headMap  参数说明 
	  * @return Map    返回类型
	 */
	public int pointConvertBindCreate(Map<String,Object> headMap);
	
	/**
	 * 检查平安万里通的充值流水是否存在
	 * @param headMap
	 * @return
	 */
	public int isExistWltOrder(Map<String,Object>  headMap);
	
	
	/**
	 * 根据万里通订单号查询芒果订单流水情况
	 * @param headMap
	 * @return
	 */
	public List<Map<String,Object>> queryPointConvertByOrder(Map<String,Object> headMap);
	
	
	/**
	 * 新增积分互换记录
	 * @param headMap
	 * @return
	 */
	public int insertPointConvert(Map<String,Object> headMap);
	
	
	/**
	 * 生成积分互换序列
	 * @param headMap
	 * @return
	 */
	public long getSeqMbrPointConvert(Map<String,Object> headMap);
	
	/**
	 * 根据万里通交易号查询总积分
	 * @param headMap
	 * @return
	 */
	public long querySumMangoPointByOrder(Map<String,Object> headMap);
	
	/**
	 * 根据兑换id修改积分兑换流水交易状态
	 * @param headMap
	 * @return
	 */
	public int updatePointConvertTranStusByPointConvertId(Map<String,Object> headMap);
}