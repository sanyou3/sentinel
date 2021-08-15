/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel;

/**
 * An enum marks resource invocation direction.
 *
 * @author jialiang.linjl
 * @author Yanming Zhou
 */
public enum EntryType {
    /**
     * Inbound traffic 入站流量 别人访问你
     */
    IN,
    /**
     * Outbound traffic 出站流量 你访问别人
     */
    OUT;

}