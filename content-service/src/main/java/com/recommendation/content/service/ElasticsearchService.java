package com.recommendation.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.content.document.ContentDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch服务类
 * 负责索引管理、文档操作和搜索功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {

    private final RestHighLevelClient elasticsearchClient;
    private final ObjectMapper objectMapper;

    private static final String CONTENT_INDEX = "content_index";

    /**
     * 初始化索引
     */
    @PostConstruct
    public void initIndex() {
        try {
            if (!indexExists(CONTENT_INDEX)) {
                createContentIndex();
                log.info("Created Elasticsearch index: {}", CONTENT_INDEX);
            }
        } catch (IOException e) {
            log.error("Failed to initialize Elasticsearch index", e);
        }
    }

    /**
     * 检查索引是否存在
     */
    public boolean indexExists(String indexName) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexName);
        return elasticsearchClient.indices().exists(request, RequestOptions.DEFAULT);
    }

    /**
     * 创建内容索引
     */
    public void createContentIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(CONTENT_INDEX);
        
        // 设置索引映射，对应设计文档中的映射结构
        String mapping = "{\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"contentId\": {\"type\": \"keyword\"},\n" +
            "      \"title\": {\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"standard\",\n" +
            "        \"fields\": {\n" +
            "          \"keyword\": {\"type\": \"keyword\"}\n" +
            "        }\n" +
            "      },\n" +
            "      \"contentType\": {\"type\": \"keyword\"},\n" +
            "      \"tags\": {\"type\": \"keyword\"},\n" +
            "      \"category\": {\"type\": \"keyword\"},\n" +
            "      \"embedding\": {\n" +
            "        \"type\": \"dense_vector\",\n" +
            "        \"dims\": 128\n" +
            "      },\n" +
            "      \"publishTime\": {\"type\": \"date\"},\n" +
            "      \"hotScore\": {\"type\": \"float\"},\n" +
            "      \"authorId\": {\"type\": \"keyword\"},\n" +
            "      \"summary\": {\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"standard\"\n" +
            "      },\n" +
            "      \"extraData\": {\"type\": \"object\"},\n" +
            "      \"createdAt\": {\"type\": \"date\"},\n" +
            "      \"updatedAt\": {\"type\": \"date\"}\n" +
            "    }\n" +
            "  },\n" +
            "  \"settings\": {\n" +
            "    \"number_of_shards\": 3,\n" +
            "    \"number_of_replicas\": 1,\n" +
            "    \"analysis\": {\n" +
            "      \"analyzer\": {\n" +
            "        \"content_analyzer\": {\n" +
            "          \"type\": \"standard\",\n" +
            "          \"stopwords\": \"_english_\"\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        request.source(mapping, XContentType.JSON);
        CreateIndexResponse response = elasticsearchClient.indices().create(request, RequestOptions.DEFAULT);
        
        if (!response.isAcknowledged()) {
            throw new RuntimeException("Failed to create index: " + CONTENT_INDEX);
        }
    }

    /**
     * 删除索引
     */
    public void deleteIndex(String indexName) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        elasticsearchClient.indices().delete(request, RequestOptions.DEFAULT);
    }

    /**
     * 索引单个文档
     */
    public String indexDocument(ContentDocument document) throws IOException {
        IndexRequest request = new IndexRequest(CONTENT_INDEX)
                .id(document.getContentId().toString())
                .source(objectMapper.writeValueAsString(document), XContentType.JSON);

        IndexResponse response = elasticsearchClient.index(request, RequestOptions.DEFAULT);
        log.debug("Indexed document with ID: {}", response.getId());
        return response.getId();
    }

    /**
     * 批量索引文档
     */
    public void bulkIndexDocuments(List<ContentDocument> documents) throws IOException {
        if (documents.isEmpty()) {
            return;
        }

        BulkRequest bulkRequest = new BulkRequest();
        
        for (ContentDocument document : documents) {
            IndexRequest indexRequest = new IndexRequest(CONTENT_INDEX)
                    .id(document.getContentId().toString())
                    .source(objectMapper.writeValueAsString(document), XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        
        if (bulkResponse.hasFailures()) {
            log.error("Bulk indexing failed: {}", bulkResponse.buildFailureMessage());
            throw new RuntimeException("Bulk indexing failed");
        }
        
        log.info("Successfully indexed {} documents", documents.size());
    }

    /**
     * 更新文档
     */
    public void updateDocument(String documentId, ContentDocument document) throws IOException {
        UpdateRequest request = new UpdateRequest(CONTENT_INDEX, documentId)
                .doc(objectMapper.writeValueAsString(document), XContentType.JSON);

        UpdateResponse response = elasticsearchClient.update(request, RequestOptions.DEFAULT);
        log.debug("Updated document with ID: {}", response.getId());
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String documentId) throws IOException {
        DeleteRequest request = new DeleteRequest(CONTENT_INDEX, documentId);
        DeleteResponse response = elasticsearchClient.delete(request, RequestOptions.DEFAULT);
        log.debug("Deleted document with ID: {}", response.getId());
    }

    /**
     * 根据ID获取文档
     */
    public ContentDocument getDocument(String documentId) throws IOException {
        GetRequest request = new GetRequest(CONTENT_INDEX, documentId);
        GetResponse response = elasticsearchClient.get(request, RequestOptions.DEFAULT);
        
        if (response.isExists()) {
            return objectMapper.readValue(response.getSourceAsString(), ContentDocument.class);
        }
        return null;
    }

    /**
     * 搜索内容
     */
    public List<ContentDocument> searchContent(String query, String contentType, 
                                             List<String> tags, int from, int size) throws IOException {
        SearchRequest searchRequest = new SearchRequest(CONTENT_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 文本搜索
        if (query != null && !query.trim().isEmpty()) {
            boolQuery.should(QueryBuilders.multiMatchQuery(query, "title", "summary")
                    .boost(2.0f))
                    .should(QueryBuilders.matchQuery("title", query).boost(1.5f));
        }

        // 内容类型过滤
        if (contentType != null && !contentType.trim().isEmpty()) {
            boolQuery.filter(QueryBuilders.termQuery("contentType", contentType));
        }

        // 标签过滤
        if (tags != null && !tags.isEmpty()) {
            boolQuery.filter(QueryBuilders.termsQuery("tags", tags));
        }

        searchSourceBuilder.query(boolQuery)
                .from(from)
                .size(size)
                .sort("hotScore", SortOrder.DESC)
                .sort("publishTime", SortOrder.DESC);

        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
        
        List<ContentDocument> results = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            ContentDocument document = objectMapper.readValue(hit.getSourceAsString(), ContentDocument.class);
            results.add(document);
        }

        return results;
    }

    /**
     * 基于向量相似度搜索
     */
    public List<ContentDocument> searchSimilarContent(List<Float> queryVector, 
                                                    String contentType, int size) throws IOException {
        SearchRequest searchRequest = new SearchRequest(CONTENT_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 向量相似度查询（这里使用脚本查询模拟，实际生产环境建议使用专门的向量搜索插件）
        if (queryVector != null && !queryVector.isEmpty()) {
            String script = "if (doc['embedding'].size() == 0) return 0;" +
                "double dotProduct = 0;" +
                "double normA = 0;" +
                "double normB = 0;" +
                "for (int i = 0; i < params.queryVector.size(); i++) {" +
                "    dotProduct += doc['embedding'][i] * params.queryVector[i];" +
                "    normA += doc['embedding'][i] * doc['embedding'][i];" +
                "    normB += params.queryVector[i] * params.queryVector[i];" +
                "}" +
                "return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));";

            boolQuery.must(QueryBuilders.scriptQuery(
                    new org.elasticsearch.script.Script(
                            org.elasticsearch.script.ScriptType.INLINE,
                            "painless",
                            script,
                            Map.of("queryVector", queryVector)
                    )
            ));
        }

        // 内容类型过滤
        if (contentType != null && !contentType.trim().isEmpty()) {
            boolQuery.filter(QueryBuilders.termQuery("contentType", contentType));
        }

        searchSourceBuilder.query(boolQuery)
                .size(size)
                .sort("_score", SortOrder.DESC);

        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
        
        List<ContentDocument> results = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            ContentDocument document = objectMapper.readValue(hit.getSourceAsString(), ContentDocument.class);
            results.add(document);
        }

        return results;
    }

    /**
     * 获取热门内容
     */
    public List<ContentDocument> getHotContent(String contentType, int size) throws IOException {
        SearchRequest searchRequest = new SearchRequest(CONTENT_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 内容类型过滤
        if (contentType != null && !contentType.trim().isEmpty()) {
            boolQuery.filter(QueryBuilders.termQuery("contentType", contentType));
        }

        // 只返回已发布的内容
        boolQuery.filter(QueryBuilders.existsQuery("publishTime"));

        searchSourceBuilder.query(boolQuery)
                .size(size)
                .sort("hotScore", SortOrder.DESC)
                .sort("publishTime", SortOrder.DESC);

        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
        
        List<ContentDocument> results = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            ContentDocument document = objectMapper.readValue(hit.getSourceAsString(), ContentDocument.class);
            results.add(document);
        }

        return results;
    }
}