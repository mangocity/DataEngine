package com.mangocity.de.mbr.sqlmapper.point;

import java.util.Map;

/**
 * 积分兑换明细
 * @author longshu.chen
 *
 */
public interface pointWalletLogMapper {
	
	/**
	 * 插入积分授权
	 * @param map
	 * @return
	 */
	public Integer insertPointWalletLog(Map<String,Object> map);
	
	/**
	 * 更具订单号和交易类型查询（支付P，退还R）
	 * @param map
	 * @return
	 */
	public Map<String, Object> selectPointWalletLogByOrdercodeAndType(Map<String,Object> map);
	
	/**
	 * 通过订单号和交易类型查询交易积分总额
	 * @param map
	 * @return
	 */
	public Long getSumPointByOrderCode(Map<String,Object> map);
	
	/**
	 * 通过原订单号和交易类型查询冲账积分总额
	 * @param map
	 * @return
	 */
	public Long getSumPointByRemark(Map<String,Object> map);
	
	/**
	 * 更具订单号和交易类型查询订单是否存在
	 * @param map
	 * @return
	 */
	public Map<String, Object> isExit(Map<String,Object> map);
}
