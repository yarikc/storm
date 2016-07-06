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
package org.apache.storm.elasticsearch.bolt;

import org.apache.storm.elasticsearch.common.EsConfig;
import org.apache.storm.elasticsearch.common.EsTupleMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.utils.TupleUtils;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Basic bolt for storing tuple to ES document.
 */
public class EsBulkIndexBolt extends AbstractEsBolt {
    private final EsTupleMapper tupleMapper;
    private BulkRequestBuilder requestBuilder;

    /**
     * EsIndexBolt constructor
     *
     * @param esConfig    Elasticsearch configuration containing node addresses and cluster name {@link EsConfig}
     * @param tupleMapper Tuple to ES document mapper {@link EsTupleMapper}
     */
    public EsBulkIndexBolt(EsConfig esConfig, EsTupleMapper tupleMapper) {
        super(esConfig);
        this.tupleMapper = checkNotNull(tupleMapper);
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        super.prepare(map, topologyContext, outputCollector);
        if (requestBuilder == null) {
            requestBuilder = client.prepareBulk();
        }
    }

    /**
     * {@inheritDoc}
     * Tuple should have relevant fields (source, index, type, id) for tupleMapper to extract ES document.
     */
    @Override
    public void execute(Tuple tuple) {
        try {
            if (TupleUtils.isTick(tuple)) {
                BulkResponse bulkResponse = requestBuilder.get();
                if (bulkResponse.hasFailures()) {
                    for (BulkItemResponse response : bulkResponse.getItems()) {
                        if (response.isFailed()) {

                        }
                    }
                }
                requestBuilder = client.prepareBulk();
                collector.ack(tuple);
                return; // Do not try to send ticks to Elastic
            }

            String source = tupleMapper.getSource(tuple);
            String index = tupleMapper.getIndex(tuple);
            String type = tupleMapper.getType(tuple);
            String id = tupleMapper.getId(tuple);

            requestBuilder.add(client.prepareIndex(index, type, id).setSource(source));
            collector.ack(tuple); // ignore all errors
            
        } catch (Exception e) {
            collector.reportError(e);
            collector.fail(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
    }
}
