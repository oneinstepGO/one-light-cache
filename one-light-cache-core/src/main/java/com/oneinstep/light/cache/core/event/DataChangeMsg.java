package com.oneinstep.light.cache.core.event;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据变更消息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataChangeMsg implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 数据名称
     */
    private String dataName;
    /**
     * 数据ID
     */
    private String dataId;
    /**
     * 数据变更类型
     */
    private DataChangeType type;

    /**
     * 数据变更类型
     */
    public enum DataChangeType {
        ADD, // 新增
        UPDATE, // 更新
        DELETE // 删除
    }
}
