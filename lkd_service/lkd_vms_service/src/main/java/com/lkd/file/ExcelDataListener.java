package com.lkd.file;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.google.common.collect.Lists;
import com.lkd.entity.SkuEntity;
import com.lkd.service.SkuService;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;

import java.util.List;

/**
 * @Description:excel导入
 * @Author susu
 * @Date 2023/8/24
 */
@Component
public class ExcelDataListener<E> extends AnalysisEventListener<E> {
    private List<SkuEntity> list = Lists.newArrayList();

    @Autowired
    private SkuService skuService;


    private static final int BATCH_COUNT = 500;//每批存储条数

    /**
     * 提取数据
     *
     * @param e
     * @param analysisContext
     */

    @Override
    public void invoke(E e, AnalysisContext analysisContext) {
        SkuEntity skuEntity = new SkuEntity();
        BeanUtils.copyProperties(e, skuEntity);
        list.add(skuEntity);

        if (list.size() >= BATCH_COUNT) {
            doAfterAllAnalysed(null);
        }

    }


    /**
     * 读取完成
     *
     * @param analysisContext
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        skuService.saveBatch(list);
    list.clear();
    }
}
