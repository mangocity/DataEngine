package com.mangocity.de.mbr.sqlmapper.passenger;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Select;

public interface passengerMapper {

	public int addPassengerInfo(Map headMap);

	public int deletePassengerInfo(Map headMap);

	public int updatePassengerInfo(Map headMap);

	public List<Map> queryPassengerInfo(Map headMap);
	
	public int addPassengerCertificateInfo(Map headMap);

	public int deletePassengerCertificateInfo(Map headMap);

	public int updatePassengerCertificateInfo(Map headMap);
	
	public List<Map> queryPassengerCertificateInfo(Map headMap);

	public Map queryPassengerDetailById(Map headMap);
	
	public List<Map> queryPassengerCertificateDetailById(Map headMap);
	
	public Map queryPassengerStatusByPassId(Map headMap);
	
	public Map queryCertificateStatusByPassId(Map headMap);
}