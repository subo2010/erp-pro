package com.skyeye.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.miemiedev.mybatis.paginator.domain.PageBounds;
import com.github.miemiedev.mybatis.paginator.domain.PageList;
import com.skyeye.common.object.InputObject;
import com.skyeye.common.object.OutputObject;
import com.skyeye.common.util.ToolUtil;
import com.skyeye.dao.PurchaseOrderDao;
import com.skyeye.erp.util.ErpConstants;
import com.skyeye.erp.util.ErpOrderNum;
import com.skyeye.service.PurchaseOrderService;

import net.sf.json.JSONArray;

@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService{
	
	@Autowired
	private PurchaseOrderDao purchaseOrderDao;

	/**
     * 获取采购单列表信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	public void queryPurchaseOrderToList(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> params = inputObject.getParams();
        params.put("userId", inputObject.getLogParams().get("id"));
        List<Map<String, Object>> beans = purchaseOrderDao.queryPurchaseOrderToList(params,
                new PageBounds(Integer.parseInt(params.get("page").toString()), Integer.parseInt(params.get("limit").toString())));
        PageList<Map<String, Object>> beansPageList = (PageList<Map<String, Object>>)beans;
        int total = beansPageList.getPaginator().getTotalCount();
        outputObject.setBeans(beans);
        outputObject.settotal(total);
	}

	/**
     * 新增采购单信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(value="transactionManager")
	public void insertPurchaseOrderMation(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		String depotheadStr = map.get("depotheadStr").toString();
		if(ToolUtil.isJson(depotheadStr)){
			String useId = ToolUtil.getSurFaceId();//单据主表id
			String userId = inputObject.getLogParams().get("id").toString();
			//处理数据
			JSONArray jArray = JSONArray.fromObject(depotheadStr);
			//产品中间转换对象，单据子表存储对象
			Map<String, Object> bean, entity;
			List<Map<String, Object>> entitys = new ArrayList<>();//单据子表实体集合信息
			BigDecimal allPrice = new BigDecimal("0");//主单总价
			BigDecimal itemAllPrice = null;//子单对象
			for(int i = 0; i < jArray.size(); i++){
				bean = jArray.getJSONObject(i);
				entity = purchaseOrderDao.queryMaterialsById(bean);
				if(entity != null && !entity.isEmpty()){
					//获取单价
					itemAllPrice = new BigDecimal(entity.get("estimatePurchasePrice").toString());
					entity.put("id", ToolUtil.getSurFaceId());
					entity.put("headerId", useId);//单据主表id
					entity.put("operNumber", bean.get("rkNum"));//数量
					//计算子单总价：单价*数量
					itemAllPrice = itemAllPrice.multiply(new BigDecimal(bean.get("rkNum").toString()));
					entity.put("allPrice", itemAllPrice);//单据子表总价
					entity.put("remark", bean.get("remark"));//备注
					entity.put("depotId", bean.get("depotId"));//仓库
					entity.put("mType", 0);//商品类型  0.普通  1.组合件  2.普通子件
					entity.put("userId", userId);
					entity.put("deleteFlag", 0);//删除标记，0未删除，1删除
					entitys.add(entity);
					//计算主单总价
					allPrice = allPrice.add(itemAllPrice);
				}
			}
			if(entitys.size() == 0){
				outputObject.setreturnMessage("请选择产品");
				return;
			}
			//单据主表对象
			Map<String, Object> depothead = new HashMap<>();
			depothead.put("id", useId);
			depothead.put("type", 2);//类型(1.出库/2.入库)
			depothead.put("subType", ErpConstants.DepoTheadSubType.PURCHASE_ORDER.getNum());//采购单
			ErpOrderNum erpOrderNum = new ErpOrderNum();
			String orderNum = erpOrderNum.getOrderNumBySubType(userId, ErpConstants.DepoTheadSubType.PURCHASE_ORDER.getNum());
			depothead.put("defaultNumber", orderNum);//初始票据号
			depothead.put("number", orderNum);//票据号
			depothead.put("operPersonId", userId);//操作员id
			depothead.put("operPersonName", inputObject.getLogParams().get("userName"));//操作员名字
			depothead.put("createTime", ToolUtil.getTimeAndToString());//创建时间
			depothead.put("operTime", map.get("operTime"));//出入库时间即单据日期
			depothead.put("organId", map.get("supplierId"));//供应商Id
			depothead.put("accountId", map.get("accountId"));//账户Id
			depothead.put("remark", map.get("remark"));//备注
			depothead.put("payType", map.get("payType"));//付款类型
			depothead.put("totalPrice", allPrice);//合计金额
			depothead.put("status", "0");//状态，0未审核、1.审核中、2.审核通过、3.审核拒绝、4.已转采购|销售
			depothead.put("userId", userId);
			depothead.put("deleteFlag", 0);//删除标记，0未删除，1删除
			purchaseOrderDao.insertPurchaseOrderMation(depothead);
			purchaseOrderDao.insertPurchaseOrderChildMation(entitys);
		}else{
			outputObject.setreturnMessage("数据格式错误");
		}
	}

	/**
     * 删除采购单信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	public void deletePurchaseOrderMationById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		map.put("userId", inputObject.getLogParams().get("id"));
		//获取采购单状态
		Map<String, Object> bean = purchaseOrderDao.queryPurchaseOrderStateById(map);
		if(bean != null && !bean.isEmpty()){
			if("0".equals(bean.get("status").toString()) || "3".equals(bean.get("status").toString())){//未提交审核或者审核拒绝的可以删除
				//删除采购单
				purchaseOrderDao.deletePurchaseOrderMationById(map);
				//删除采购单关联产品
				purchaseOrderDao.deletePurchaseOrderNormsMationById(map);
			}
		}else{
			outputObject.setreturnMessage("该采购单状态已经改变或数据不存在.");
		}
	}

	/**
     * 采购单信息编辑回显
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	public void queryPurchaseOrderToEditById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		map.put("userId", inputObject.getLogParams().get("id"));
		//获取采购单主单信息
		Map<String, Object> bean = purchaseOrderDao.queryPurchaseOrderToEditById(map);
		if(bean != null && !bean.isEmpty()){
			List<Map<String, Object>> norms = purchaseOrderDao.queryPurchaseOrderNormsToEditById(map);
			bean.put("norms", norms);
			outputObject.setBean(bean);
			outputObject.settotal(1);
		}else{
			outputObject.setreturnMessage("该数据不存在");
		}
	}

	/**
     * 编辑采购单信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(value="transactionManager")
	public void editPurchaseOrderMationById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		String depotheadStr = map.get("depotheadStr").toString();
		if(ToolUtil.isJson(depotheadStr)){
			String useId = map.get("id").toString();//单据主表id
			String userId = inputObject.getLogParams().get("id").toString();
			//处理数据
			JSONArray jArray = JSONArray.fromObject(depotheadStr);
			//产品中间转换对象，单据子表存储对象
			Map<String, Object> bean, entity;
			List<Map<String, Object>> entitys = new ArrayList<>();//单据子表实体集合信息
			BigDecimal allPrice = new BigDecimal("0");//主单总价
			BigDecimal itemAllPrice = null;//子单对象
			for(int i = 0; i < jArray.size(); i++){
				bean = jArray.getJSONObject(i);
				entity = purchaseOrderDao.queryMaterialsById(bean);
				if(entity != null && !entity.isEmpty()){
					//获取单价
					itemAllPrice = new BigDecimal(entity.get("estimatePurchasePrice").toString());
					entity.put("id", ToolUtil.getSurFaceId());
					entity.put("headerId", useId);//单据主表id
					entity.put("operNumber", bean.get("rkNum"));//数量
					//计算子单总价：单价*数量
					itemAllPrice = itemAllPrice.multiply(new BigDecimal(bean.get("rkNum").toString()));
					entity.put("allPrice", itemAllPrice);//单据子表总价
					entity.put("remark", bean.get("remark"));//备注
					entity.put("depotId", bean.get("depotId"));//仓库
					entity.put("mType", 0);//商品类型  0.普通  1.组合件  2.普通子件
					entity.put("userId", userId);
					entity.put("deleteFlag", 0);//删除标记，0未删除，1删除
					entitys.add(entity);
					//计算主单总价
					allPrice = allPrice.add(itemAllPrice);
				}
			}
			if(entitys.size() == 0){
				outputObject.setreturnMessage("请选择产品");
				return;
			}
			//单据主表对象
			Map<String, Object> depothead = new HashMap<>();
			depothead.put("id", useId);
			depothead.put("operTime", map.get("operTime"));//出入库时间即单据日期
			depothead.put("organId", map.get("supplierId"));//供应商Id
			depothead.put("accountId", map.get("accountId"));//账户Id
			depothead.put("remark", map.get("remark"));//备注
			depothead.put("payType", map.get("payType"));//付款类型
			depothead.put("totalPrice", allPrice);//合计金额
			depothead.put("userId", userId);
			//编辑采购单
			purchaseOrderDao.editPurchaseOrderMationById(depothead);
			//删除采购单关联产品
			purchaseOrderDao.deletePurchaseOrderNormsMationById(map);
			//新增新的关联产品
			purchaseOrderDao.insertPurchaseOrderChildMation(entitys);
		}else{
			outputObject.setreturnMessage("数据格式错误");
		}
	}

	/**
     * 采购单信息提交审核
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	@Transactional(value="transactionManager")
	public void editPurchaseOrderStateToSubExamineById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		map.put("userId", inputObject.getLogParams().get("id"));
		//获取采购单状态
		Map<String, Object> bean = purchaseOrderDao.queryPurchaseOrderStateById(map);
		if(bean != null && !bean.isEmpty()){
			if("0".equals(bean.get("status").toString()) || "3".equals(bean.get("status").toString())){//未提交审核或者审核拒绝的可以提交
				purchaseOrderDao.editPurchaseOrderStateToSubExamineById(map);
			}
		}else{
			outputObject.setreturnMessage("该采购单状态已经改变或数据不存在.");
		}
	}

	/**
     * 采购单信息审核
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	@Transactional(value="transactionManager")
	public void editPurchaseOrderStateToExamineById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		map.put("userId", inputObject.getLogParams().get("id"));
		//获取采购单状态
		Map<String, Object> bean = purchaseOrderDao.queryPurchaseOrderStateById(map);
		if(bean != null && !bean.isEmpty()){
			if("1".equals(bean.get("status").toString())){//审核中的可以进行审核
				String upStatus = map.get("status").toString();
				if("1".equals(upStatus)){//审核通过
					map.put("status", 2);
				}else if("0".equals(upStatus)){//审核不通过
					map.put("status", 3);
				}else{
					outputObject.setreturnMessage("参数错误");
					return;
				}
				purchaseOrderDao.editPurchaseOrderStateToExamineById(map);
			}
		}else{
			outputObject.setreturnMessage("该采购单状态已经改变或数据不存在.");
		}
	}
	
}