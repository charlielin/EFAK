/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.web.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.smartloli.kafka.eagle.common.protocol.BrokersInfo;
import org.smartloli.kafka.eagle.common.protocol.DashboardInfo;
import org.smartloli.kafka.eagle.common.protocol.KpiInfo;
import org.smartloli.kafka.eagle.common.protocol.TopicCapacityInfo;
import org.smartloli.kafka.eagle.common.protocol.cache.BrokerCache;
import org.smartloli.kafka.eagle.common.protocol.topic.TopicLogSize;
import org.smartloli.kafka.eagle.common.protocol.topic.TopicRank;
import org.smartloli.kafka.eagle.common.util.CalendarUtils;
import org.smartloli.kafka.eagle.common.util.KConstants;
import org.smartloli.kafka.eagle.common.util.KConstants.Topic;
import org.smartloli.kafka.eagle.common.util.StrUtils;
import org.smartloli.kafka.eagle.common.util.SystemConfigUtils;
import org.smartloli.kafka.eagle.core.factory.KafkaFactory;
import org.smartloli.kafka.eagle.core.factory.KafkaService;
import org.smartloli.kafka.eagle.core.factory.v2.BrokerFactory;
import org.smartloli.kafka.eagle.core.factory.v2.BrokerService;
import org.smartloli.kafka.eagle.web.dao.MBeanDao;
import org.smartloli.kafka.eagle.web.dao.TopicDao;
import org.smartloli.kafka.eagle.web.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitoring panel realizes data interface calling logic.
 *
 * @author smartloli.
 * <p>
 * Created by Jun 02, 2022.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /**
     * Kafka service interface.
     */
    private KafkaService kafkaService = new KafkaFactory().create();

    /**
     * Broker service interface.
     */
    private static BrokerService brokerService = new BrokerFactory().create();

    @Autowired
    private TopicDao topicDao;

    @Autowired
    private MBeanDao mbeanDao;

    /**
     * Get kafka & dashboard dataset.
     */
    @Override
    public String getDashboardPanel(String clusterAlias) {
        JSONObject target = new JSONObject();
        // target.put("kafka", kafkaBrokersGraph(clusterAlias));
        target.put("dashboard", panel(clusterAlias));
        return target.toJSONString();
    }

    private String chart(String clusterAlias, String flag) {
        Map<String, Object> params = new HashMap<>();
        params.put("cluster", clusterAlias);
        params.put("type", KConstants.CollectorType.KAFKA);
        params.put("key", KConstants.MBean.MESSAGEIN);
        params.put("tm", CalendarUtils.getCustomLastDay(0));

        if ("broker_chart".equals(flag)) {
            params.put("size", 6);// display broker lastest 6 minutes
            List<KpiInfo> kpis = mbeanDao.getDashboradPanelBrokerChart(params);
            for (KpiInfo kpi : kpis) {

            }
        } else if ("broker_chart_rate".equals(flag)) {
            params.put("size", 12);// cacl broker rate
        } else if ("broker_chart_msg".equals(flag)) {
            params.put("size", 10); // Broker MessageIn

        }

        return "";
    }

    /**
     * Get kafka data.
     */
    private String kafkaBrokersGraph(String clusterAlias) {
        List<BrokersInfo> brokers = BrokerCache.META_CACHE.get(clusterAlias);
        JSONObject target = new JSONObject();
        target.put("name", "Kafka Brokers");
        JSONArray targets = new JSONArray();
        int count = 0;
        for (BrokersInfo broker : brokers) {
            if (count > KConstants.D3.SIZE) {
                JSONObject subTarget = new JSONObject();
                subTarget.put("name", "...");
                targets.add(subTarget);
                break;
            } else {
                JSONObject subTarget = new JSONObject();
                subTarget.put("name", broker.getHost() + ":" + broker.getPort());
                targets.add(subTarget);
            }
            count++;
        }
        target.put("children", targets);
        return target.toJSONString();
    }

    /**
     * Get dashboard data.
     */
    private String panel(String clusterAlias) {
        int zks = SystemConfigUtils.getPropertyArray(clusterAlias + ".zk.list", ",").length;
        DashboardInfo dashboard = new DashboardInfo();
        dashboard.setBrokers(brokerService.brokerNumbers(clusterAlias));
        dashboard.setTopics(brokerService.topicNumbers(clusterAlias));
        dashboard.setZks(zks);
        dashboard.setConsumers(kafkaService.getKafkaConsumerGroups(clusterAlias));
        return dashboard.toString();
    }

    /**
     * Get topic rank data,such as logsize and topic capacity.
     */
    @Override
    public JSONArray getTopicRank(Map<String, Object> params) {
        List<TopicRank> topicRank = topicDao.readTopicRank(params);
        JSONArray array = new JSONArray();
        if (Topic.LOGSIZE.equals(params.get("tkey"))) {
            int index = 1;
            for (int i = 0; i < 10; i++) {
                JSONObject object = new JSONObject();
                if (i < topicRank.size()) {
                    object.put("id", index);
                    object.put("topic", "<a href='/topic/meta/" + topicRank.get(i).getTopic() + "/'>" + topicRank.get(i).getTopic() + "</a>");
                    object.put("logsize", topicRank.get(i).getTvalue());
                } else {
                    object.put("id", index);
                    object.put("topic", "");
                    object.put("logsize", "");
                }
                index++;
                array.add(object);
            }
        } else if (Topic.CAPACITY.equals(params.get("tkey"))) {
            int index = 1;
            for (int i = 0; i < 10; i++) {
                JSONObject object = new JSONObject();
                if (i < topicRank.size()) {
                    object.put("id", index);
                    object.put("topic", "<a href='/topic/meta/" + topicRank.get(i).getTopic() + "/'>" + topicRank.get(i).getTopic() + "</a>");
                    object.put("capacity", StrUtils.stringify(topicRank.get(i).getTvalue()));
                } else {
                    object.put("id", index);
                    object.put("topic", "");
                    object.put("capacity", "");
                }
                index++;
                array.add(object);
            }
        } else if (Topic.BYTE_IN.equals(params.get("tkey"))) {
            int index = 1;
            for (int i = 0; i < 10; i++) {
                JSONObject object = new JSONObject();
                if (i < topicRank.size()) {
                    object.put("id", index);
                    object.put("topic", "<a href='/topic/meta/" + topicRank.get(i).getTopic() + "/'>" + topicRank.get(i).getTopic() + "</a>");
                    object.put("byte_in", StrUtils.stringify(topicRank.get(i).getTvalue()));
                } else {
                    object.put("id", index);
                    object.put("topic", "");
                    object.put("byte_in", "");
                }
                index++;
                array.add(object);
            }
        } else if (Topic.BYTE_OUT.equals(params.get("tkey"))) {
            int index = 1;
            for (int i = 0; i < 10; i++) {
                JSONObject object = new JSONObject();
                if (i < topicRank.size()) {
                    object.put("id", index);
                    object.put("topic", "<a href='/topic/meta/" + topicRank.get(i).getTopic() + "/'>" + topicRank.get(i).getTopic() + "</a>");
                    object.put("byte_out", StrUtils.stringify(topicRank.get(i).getTvalue()));
                } else {
                    object.put("id", index);
                    object.put("topic", "");
                    object.put("byte_out", "");
                }
                index++;
                array.add(object);
            }
        }
        return array;
    }

    /**
     * Write statistics topic rank data from kafka jmx & insert into table.
     */
    public int writeTopicRank(List<TopicRank> topicRanks) {
        return topicDao.writeTopicRank(topicRanks);
    }

    /**
     * Write statistics topic logsize data from kafka jmx & insert into table.
     */
    public int writeTopicLogSize(List<TopicLogSize> topicLogSize) {
        return topicDao.writeTopicLogSize(topicLogSize);
    }

    /**
     * Get os memory data.
     */
    public String getOSMem(Map<String, Object> params) {
        List<KpiInfo> kpis = mbeanDao.getOsMem(params);
        JSONObject object = new JSONObject();
        if (kpis.size() == 2) {
            long valueFirst = Long.parseLong(kpis.get(0).getValue());
            long valueSecond = Long.parseLong(kpis.get(1).getValue());
            if (valueFirst >= valueSecond) {
                object.put("mem", StrUtils.numberic(((valueFirst - valueSecond) * 100.0 / valueFirst) + ""));
            } else {
                object.put("mem", StrUtils.numberic(((valueSecond - valueFirst) * 100.0 / valueSecond) + ""));
            }
        } else {
            object.put("mem", "0.0");
        }
        return object.toJSONString();
    }

    /**
     * Get used cpu data.
     */
    public String getUsedCPU(Map<String, Object> params) {
        List<KpiInfo> kpis = mbeanDao.getUsedCPU(params);
        JSONObject object = new JSONObject();
        if (kpis.size() > 0) {
            object.put("cpu", StrUtils.numberic(kpis.get(0).getValue()) / brokerService.brokerNumbers(params.get("cluster").toString()));
        } else {
            object.put("cpu", "0.0");
        }
        return object.toJSONString();
    }

    @Override
    public String getActiveTopicNumbers(String clusterAlias, Map<String, Object> params) {
        long activeNums = topicDao.getActiveTopicNumbers(params);
        TopicCapacityInfo topicCapacityInfo = topicDao.getTopicCapacityScatter(params);
        JSONObject object = new JSONObject();
        object.put("active", activeNums);
        object.put("standby", brokerService.topicList(clusterAlias).size() - activeNums);
        object.put("total", brokerService.topicList(clusterAlias).size());
        if (topicCapacityInfo != null) {
            object.put("mb", topicCapacityInfo.getMb());
            object.put("gb", topicCapacityInfo.getGb());
            object.put("tb", topicCapacityInfo.getTb());
        } else {
            object.put("mb", 0);
            object.put("gb", 0);
            object.put("tb", 0);
        }
        return object.toJSONString();
    }

    /**
     * Read topic lastest logsize diffval data.
     */
    public TopicLogSize readLastTopicLogSize(Map<String, Object> params) {
        return topicDao.readLastTopicLogSize(params);
    }

    /**
     * Get all clean topic list.
     */
    public List<TopicRank> getCleanTopicList(Map<String, Object> params) {
        return topicDao.getCleanTopicList(params);
    }

    @Override
    public List<TopicRank> getAllTopicRank(Map<String, Object> params) {
        return topicDao.getAllTopicRank(params);
    }

    @Override
    public void removeTopicRank(Map<String, Object> params) {
        topicDao.removeTopicRank(params);
    }

}