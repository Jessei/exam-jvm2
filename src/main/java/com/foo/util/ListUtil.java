package com.foo.util;

import java.util.ArrayList;
import java.util.List;

public class ListUtil {
	
	/**
	 * 
	 * @description:按照指定长度将list分割
	 * @author:bigWang
	 * 2018年9月16日
	 * @param <T>
	 *
	 * @param list
	 * @param groupSize
	 * @return
	 */
	public static <T> List<List<T>> splitList(List<T> list , int groupSize){
		if (list == null || list.size()==0 || groupSize == 0) {
			return null;
		}
        int length = list.size();
        // 计算可以分成多少组
        //length % groupSize == 0 ? length / groupSize : length / groupSize +1
        int num = ( length + groupSize - 1 )/groupSize ;
        List<List<T>> newList = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            // 开始位置
            int fromIndex = i * groupSize;
            // 结束位置
            int toIndex = (i+1) * groupSize < length ? ( i+1 ) * groupSize : length ;
            newList.add(list.subList(fromIndex,toIndex)) ;
        }
        return  newList ;
    }

}
