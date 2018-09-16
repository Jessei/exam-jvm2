package com.foo;

import java.util.List;

/**
 * 
 * @description: aggregation
 * @author:bigWang
 * 2018年9月15日
 *
 */
public interface AggregationService {
	
	List<SkuEntity> SkuAggregation(List<String> ids);
}
