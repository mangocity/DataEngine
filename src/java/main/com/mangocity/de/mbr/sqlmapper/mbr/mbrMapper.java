package com.mangocity.de.mbr.sqlmapper.mbr;

import java.util.List;
import java.util.Map;

public interface mbrMapper {
	/**
	 * 
	 * @Title: mbrCreate
	 * @Description: TODO(插入mbr信息)
	 * @param  headMap 参数说明
	 * @return void 返回类型
	 */
	public void mbrCreate(Map headMap);

	/**
	 * @Title:queryMbrByMbrshipCd
	 * @Description: (根据会籍编码查询会员信息)
	 * @param  headMap 参数说明
	 * @return Map 返回类型
	 */
	public Map queryMbrByMbrshipCd(Map headMap);
	
	/**
	 * queryMbrByMbrId
	 * @Description: (根据mbrId查询会员信息)
	 * @param  headMap 参数说明
	 * @return Map 
	 */
	public Map queryMbrByMbrId(Map headMap);
	
	/**
	 * getSeq12
	 * @Description: (取得会员编码的12位seq)
	 * @param
	 * @return Integer mbrId
	 */
	public Long getSeq12();
	
	/**
	* @Title: getMbrId 
	* @Description: (获得会员id,序列生成) 
	* @param 
	* @return Long    返回类型
	 */
	public Long getMbrId();
	
	/**
	* @Title: 修改t_mbr表信息
	* @Description: 根据mbrId查询绑定手机 
	* @param 
	* @return int    返回类型
	 */
	public int updateMbrInfo(Map headMap);
	
	/**
	* @Title: 创建mbr更新日志
	* @param 
	* @return int    返回类型
	 */
	public int mbrUpdateLogCreate(Map headMap);
	
	/**
	 * 修改用户的Attribute属性，用户芒果网和万里通积分兑换授权的时候使用
	 * @param headMap
	 * @return
	 */
	public int updateMbrAttribute(Map<String, Object> headMap);
	
	public List<Map<String,Object>> validateLogin(Map<String,Object> headMap);
	
}