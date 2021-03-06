package com.mangocity.de.mbr.sqlmapper;

import static com.mangocity.ce.util.CommonUtils.isBlank;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.util.New;
import com.mangocity.de.mbr.util.SqlUtil;

public class passengerMapperTest {

	@Test
	public void testQueryPassengerInfo() throws ExceptionAbstract {
		SqlUtil.getInstance().init();
		Map<String, Object> headMap = New.map();
		headMap.put("MBR_ID", "857642");
		headMap.put("startNum", 1);
		headMap.put("endNum", 10);
		// #step1:查出所有该会员的旅客信息和对应的证件信息
		List<Map<String, Object>> passengerList = SqlUtil.getInstance()
				.selectList("queryPassengerInfo", headMap);

		// #step2:如果该会员对应的所有旅客没有证件信息,则直接返回旅客信息
		if (null == passengerList || passengerList.size() == 0) {
			throw new DatabaseException(this,
					ErrorConstant.ERROR_PARAM_NULL_10000, "该会员没有常用旅客");
		}

		List<Map<String, Object>> totalList = New.list();// 需要接口返回的列表
		List<String> passIdList = New.list();
		for (Map<String, Object> passenerMap : passengerList) {
			passIdList.add(String.valueOf(passenerMap.get("PASS_ID")));
		}

		//#step3:查询出指定旅客的所有证件信息
		Map<String, Object> tMap = New.map();
		tMap.put("list", passIdList);
		List<Map<String, Object>> passengerCertificateList = SqlUtil
				.getInstance()
				.selectList("queryPassengerCertificateInfo", tMap);

		//如果查询出来的证件信息为空,则直接返回旅客信息
		if (null == passengerCertificateList
				|| passengerCertificateList.size() == 0) {
			//return passengerList;
		}

		// #step4:迭代所有旅客信息和证件信息,过滤一个旅客下的所有的证件新
		String passId = null;
		String _passId = null;
		List<Map<String,Object>> filterList =  null;
		for (Map<String, Object> passenerMap : passengerList) {
			filterList = New.list();
			for (Map<String, Object> passengerCertificateMap : passengerCertificateList) {
				passId = String.valueOf(passenerMap.get("PASS_ID"));
				_passId = String.valueOf(passengerCertificateMap.get("PASS_ID"));
				if (isBlank(passId) || isBlank(_passId)) {
					continue;
				} else if (passId.equals(_passId)) {// 则把证件信息合并到同一旅客下面
					filterList.add(passengerCertificateMap);
				}
			}
			passenerMap.put("certificateList", filterList);
			totalList.add(passenerMap);
		}
		
		System.out.println(JSON.toJSONString(totalList));
	}
}
