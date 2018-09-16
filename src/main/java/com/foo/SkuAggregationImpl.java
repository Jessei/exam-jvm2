package com.foo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.foo.util.BigDecimalSummaryStatistics;
import com.foo.util.ListUtil;

public class SkuAggregationImpl implements AggregationService {
	
	
	
	private static final int SKUID_LIMIT = 100;
	private static final int SKUSERVICEID_LIMIT = 20;
	
	
	/*
	 * 1.接口数据清理清洗，去掉无用数据
	 * 2.融合成一个大而全的List List<SkuEntity>
	 */
	@Override
	public List<SkuEntity> SkuAggregation(List<String> ids) {
		
		if (ids.size() > SKUID_LIMIT) {
            throw new RuntimeException("不能超过"+SKUID_LIMIT+"个 skuId");
        }
		
		List<SkuInfoDTO> findByIds = new ArrayList<>(ids.size());
		
    	//接口每次调用只允许传20个ID 需要将拆分成几个子list
    	List<List<String>> splitList = ListUtil.splitList(ids, SKUSERVICEID_LIMIT);
    	//循环调用接口 将返回数据组装到List<SkuInfoDTO>
    	splitList.stream().map(it -> ServiceBeanFactory.getInstance().getServiceBean(SkuService.class).findByIds(it))
    			.forEach(it->{it.forEach(skuInfoDTO -> findByIds.add(skuInfoDTO));		
    			});
		//findByIds has init
    	
		//清洗无用数据   规则：原始商品(ORIGIN), 清除货号为空的;数字化商品(DIGITAL), 清除SPUId为空的
		findByIds.removeIf(SkuInfoDTO -> 
		           (("ORIGIN").equals(SkuInfoDTO.getSkuType()) && (SkuInfoDTO.getArtNo() == null || "".equals(SkuInfoDTO.getArtNo()))) 
				|| (("DIGITAL").equals(SkuInfoDTO.getSkuType()) && (SkuInfoDTO.getSpuId() == null || "".equals(SkuInfoDTO.getSpuId()))));
		//findByIds data has filter
		
		//调用接口查询出各维度的数据 --全集
		Map<String, SkuInfoDTO> skuInfoMap =findByIds.stream().collect(Collectors.toMap(SkuInfoDTO::getId, Function.identity()));
		//通过接口融合为List<SkuEntity>
		List<SkuEntity>  skuEntity= ids.stream().map(it -> {
			SkuInfoDTO SkuInfoDTO = skuInfoMap.get(it);
            if (SkuInfoDTO != null) {
                return new SkuEntity(SkuInfoDTO.getId(), SkuInfoDTO.getName(), SkuInfoDTO.getArtNo(),
                		SkuInfoDTO.getSpuId(), SkuInfoDTO.getSkuType(),
                		ServiceBeanFactory.getInstance().getServiceBean(PriceService.class).getBySkuId(SkuInfoDTO.getId()),
                		ServiceBeanFactory.getInstance().getServiceBean(InventoryService.class).getBySkuId(SkuInfoDTO.getId()));
            } else {
                return null;
            }
        }).collect(Collectors.toList());
		skuEntity.removeIf(Objects::isNull);
		//date are ready
		
		//对List<SkuEntity> 进行聚合操作  两种情况 
		//分组
		Map<String, List<SkuEntity>> originList =  skuEntity.stream().filter(it -> "ORIGIN".equals(it.getSkuType())).collect(Collectors.groupingBy(SkuEntity::getArtNo));
		Map<String, List<SkuEntity>> digitalList =  skuEntity.stream().filter(it -> "DIGITAL".equals(it.getSkuType())).collect(Collectors.groupingBy(SkuEntity::getSpuId));
		Map<String,Map<String, List<SkuEntity>>> statisticsList = new HashMap<>();
		statisticsList.put("ORIGIN", originList);
		statisticsList.put("DIGITAL",digitalList);
		//统计
		List<SkuEntityShow> skuEntityShow = SkuStatistics(statisticsList);

		print(skuEntityShow);;
		return null;
	}
	/**
	 * 
	 * @description:1.分组统计
	 * 				2.origin与digital 合并 
	 * @author:bigWang
	 * 2018年9月16日
	 *
	 * @param originList
	 * @return
	 */
	private List<SkuEntityShow> SkuStatistics(Map<String,Map<String, List<SkuEntity>>> statisticsList){
		if (statisticsList == null) {
			return null;
		}
		List<SkuEntityShow> skuEntityShow = new ArrayList<>();
		for (String skuType : statisticsList.keySet()) {
			Map<String, List<SkuEntity>> skuMap = statisticsList.get(skuType);
		//对分组进行统计 多条list 转化为一条
			for (String k : skuMap.keySet()) {
				SkuEntityShow show = new SkuEntityShow();
				List<SkuEntity> list = skuMap.get(k);
				
				//聚合的id
				String ids = list.stream().map(it->it.getId()).collect(Collectors.joining(","));
				show.setId(ids);
				//名称取一个
				if (list != null && list.size() > 0) {
					show.setName(list.get(0).getName());
				}
				//origin与digital 分开
				if ("ORIGIN".equals(skuType)) {
					//货号
					show.setArtNo(k);
					//spu_id
					show.setSpuId("");
				}else{
					//货号
					show.setArtNo("");
					//spu_id
					show.setSpuId(k);
				}
				show.setSkuType(skuType);
				
				//全渠道库存汇总
				BigDecimal inventory = list.stream().map(it -> it.getInventoryDTOS().stream().map(ChannelInventoryDTO::getInventory).reduce(BigDecimal.ZERO, BigDecimal::add)).reduce(BigDecimal.ZERO, BigDecimal::add);
				show.setInventory(inventory.toPlainString());
				//价格
				BigDecimalSummaryStatistics bdsPrice = list.stream().map(SkuEntity::getPrice).collect(BigDecimalSummaryStatistics.statistics());
				if (bdsPrice.getMin().compareTo(bdsPrice.getMax()) == 0) {
					show.setPrice(bdsPrice.getMin().toPlainString());
				}else
					show.setPrice(bdsPrice.getMin().toPlainString()+"~"+bdsPrice.getMax().toPlainString());
				skuEntityShow.add(show);
			}
		}
			return skuEntityShow;
	}
	
	//输出格式化
	static Formatter formatter = new Formatter(System.out);
    public static void print(List<SkuEntityShow> list) {
    	formatter.format("%-40s %-30s %-12s %-40s %-30s\n", "ITEM商品名称", "货号","SPU ID","全渠道库存汇总","价格");
    	for (SkuEntityShow skuEntityShow : list) {
    		formatter.format("%-15s %-10s %-15s %-15s %-15s \n", skuEntityShow.getName(), skuEntityShow.getArtNo(),skuEntityShow.getSpuId(),skuEntityShow.getInventory(),skuEntityShow.getPrice());
		}
	}

}
