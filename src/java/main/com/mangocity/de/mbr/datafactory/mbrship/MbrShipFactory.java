package com.mangocity.de.mbr.datafactory.mbrship;

import static com.mangocity.ce.util.CommonUtils.isBlankIncludeNull;
import static com.mangocity.ce.util.CommonUtils.isEmpty;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorCode;

import com.mangocity.ce.bean.EngineBean;
import com.mangocity.ce.book.ErrorBook;
import com.mangocity.ce.exception.BusinessException;
import com.mangocity.ce.exception.DatabaseException;
import com.mangocity.ce.exception.ExceptionAbstract;
import com.mangocity.ce.exception.SystemException;
import com.mangocity.ce.util.AssertUtils;
import com.mangocity.ce.util.CommonUtils;
import com.mangocity.ce.util.MD5Algorithm;
import com.mangocity.ce.util.New;
import com.mangocity.ce.book.ErrorConstant;
import com.mangocity.ce.book.ConstantArgs;
import com.mangocity.de.mbr.util.MbrCdUtil;
import com.mangocity.de.mbr.util.SqlUtil;

/**
 * 
 * @ClassName: MbrFactory
 * @Description: TODO(会籍数据工厂)
 * @author Syungen
 * @date 2015年8月25日 下午6:19:22
 *
 */
public class MbrShipFactory {
	private static final Logger log = Logger.getLogger(MbrShipFactory.class);

	/**
	 * @Title: MbrRegister
	 * @Description: TODO(注册会员)
	 * @param  pb
	 * @param @return
	 * @param @throws ExceptionAbstract 参数说明
	 * @return Map 返回类型
	 * @throws ParseException 
	 * @throws NumberFormatException 
	 */
	public List queryMbrShipListByMbrid(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrShipFactory queryMbrShipListByMbrid begin()...param: " + pb.getHeadMap());
		List outList  = SqlUtil.getInstance().selectList("queryMbrShipListByMbrid", pb.getHeadMap());
		log.info("MbrShipFactory queryMbrShipListByMbrid"+outList);
		return outList;
	}
	
	/**
	 * 根据会籍编号查询会籍信息
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String,Object> queryMbrShipByMbrshipCd(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrShipFactory queryMbrShipByMbrshipCd begin()...param: " + pb.getHeadMap());
		Map<String,Object> mbrshipMap = SqlUtil.getInstance().selectOne("queryMbrShipByMbrshipCd", pb.getHeadMap());
		log.info("MbrShipFactory queryMbrShipByMbrshipCd data: "+mbrshipMap);
		
		if(CommonUtils.isEmpty(mbrshipMap)){
			throw new BusinessException(this, ErrorConstant.ERROR_NO_RESULT_DATA, "根据该会籍编码查询不到会籍信息");
		}
		if(isBlankIncludeNull(String.valueOf(mbrshipMap.get("STUS")))
				||!ConstantArgs.STATUS_ACTIVE.equals(String.valueOf(mbrshipMap.get("STUS")))){
			throw new BusinessException(this, ErrorConstant.Mbrship.ERROR_MBRSHIP_IS_NOT_ACTIVED, "会籍状态没有激活");
		}
		return mbrshipMap;
	}
	/**
	 * 通过会籍编码和联名卡号查询
	 * @param pb
	 * @return
	 * @throws ExceptionAbstract
	 */
	public Map<String,Object> queryMbrshipBymbrshipCategoryIdAndAliasNo(EngineBean pb) throws ExceptionAbstract {
		log.info("MbrShipFactory queryMbrshipBymbrshipCategoryIdAndAliasNo begin()...param: " + pb.getHeadMap());
		Map<String,Object> mbrshipMap = SqlUtil.getInstance().selectOne("queryMbrshipBymbrshipCategoryIdAndAliasNo", pb.getHeadMap());
		log.info("MbrShipFactory queryMbrshipBymbrshipCategoryIdAndAliasNo data: "+mbrshipMap);
		return mbrshipMap;
	}
	
	/**
	 * 通过shipCd查询信息
	 * @param headMap
	 * @return
	 */
	public Map<String, Object> queryMbrShipByShipCd(EngineBean pb) throws ExceptionAbstract{
		log.info("MbrShipFactory queryMbrShipByShipCd begin()...param: " + pb.getHeadMap());
		Map<String,Object> mbrshipMap = New.map();
		mbrshipMap=SqlUtil.getInstance().selectOne("queryMbrShipByShipCd", pb.getHeadMap());
		log.info("MbrShipFactory queryMbrShipByShipCd data: "+mbrshipMap);
		return mbrshipMap;
	}
}
