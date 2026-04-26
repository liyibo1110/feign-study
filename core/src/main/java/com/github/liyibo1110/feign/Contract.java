package com.github.liyibo1110.feign;

import java.util.List;

/**
 * 负责：将Method转换成MethodMetadata
 * @author liyibo
 * @date 2026-04-24 16:47
 */
public interface Contract {

    /**
     * Class -> List<MethodMetadata>
     */
    List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType);

    abstract class BaseContract implements Contract {
        @Override
        public List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {

        }
    }
}
