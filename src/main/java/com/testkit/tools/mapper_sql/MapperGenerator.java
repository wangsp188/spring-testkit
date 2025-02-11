package com.testkit.tools.mapper_sql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.testkit.TestkitHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.*;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapperGenerator {

    /**
     * 构建sql
     *
     * @param xmlContent
     * @param statementId
     * @param prepared
     * @param parameters
     * @return
     * @throws Exception
     */
    public static String generateSql(String xmlContent, String statementId, boolean prepared, JSONObject parameters) throws Exception {
        xmlContent = cleanXml(xmlContent, statementId);
        // Step 1: Initialize MyBatis Configuration and parse XML content
        // 使用正则表达式移除typeHandler属性 (注意：正则可能影响其他配置，请具体分析XML结构）
        // Step 2: Parse the mapper XML and build a MappedStatement
        Configuration configuration = new Configuration();
        try {
            // Initialize an environment (we don't need actual data source for SQL generation)
            Environment environment = new Environment(TestkitHelper.getPluginName(), new JdbcTransactionFactory(), new FakeDataSource());
            configuration.setEnvironment(environment);
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(new StringReader(xmlContent), configuration, TestkitHelper.getPluginName(), configuration.getSqlFragments());
            mapperBuilder.parse(); // Parses the XML and adds statements to configuration
        } catch (Throwable e) {
            throw new RuntimeException("mapper parse error，errorType:" + e.getClass().getName() + ", " + e.getMessage());
        }

        // Step 3: Get the MappedStatement by statementId
        MappedStatement mappedStatement = configuration.getMappedStatement(statementId);
        SqlSource sqlSource = mappedStatement.getSqlSource();
        BoundSql boundSql = sqlSource.getBoundSql(parameters);

        // Step 4: Prepare SQL with parameters
        String sql = boundSql.getSql();
        if (prepared) {
            return sql;
        }
        // Step 5: Generate final SQL by replacing placeholders with actual values
        return buildCompleteSql(sql, boundSql, parameters);
    }


    /**
     * 构建sql参数模板
     *
     * @param xmlContent
     * @param statementId
     * @return
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws TransformerException
     */
    public static JSONObject buildParamTemplate(String xmlContent, String statementId) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        xmlContent = cleanXml(xmlContent, statementId);
        Document document = parseXml(xmlContent);
        Element rootElement = document.getDocumentElement();

        Map<String, Boolean> paramMap = new HashMap<>();
        extractParametersFromElement(rootElement, paramMap);

        return generateJsonTemplate(paramMap);
    }

    private static String buildCompleteSql(String sql, BoundSql boundSql, JSONObject parameters) {
        // Iterate over parameters and replace "?" placeholders
        try {
            StringBuilder finalSql = new StringBuilder();
            String[] sqlParts = sql.split("\\?");
            List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
            for (int i = 0; i < sqlParts.length; i++) {
                finalSql.append(sqlParts[i]);
                if (parameterMappings.size() > i) {
                    ParameterMapping parameterMapping = parameterMappings.get(i);
                    String paramName = parameterMapping.getProperty();
                    Object paramValue = JSONPath.eval(parameters, "$." + paramName);
                    if (paramValue == null) {
                        paramValue = boundSql.getAdditionalParameter(paramName);
                    }
                    //  根据类型jdbcType和 paramValue 拼接参数
                    if (paramValue == null) {
                        finalSql.append("NULL");
                    } else if (paramValue instanceof String) {
                        finalSql.append("'").append(paramValue).append("'");
                    } else {
                        finalSql.append(paramValue);
                    }
                }
            }
//        finalSql.append(sqlParts[sqlParts.length-1]);
            return finalSql.toString();
        } catch (Throwable e) {
            throw new RuntimeException("params replace error，errorType:" + e.getClass().getName() + ", " + e.getMessage());
        }
    }


    private static @NotNull String cleanXml(String xmlContent, String statementId) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        // Step 1: Parse XML and find <mapper> node
        Document document = parseXml(xmlContent);
        String dynamicXmlContent = null;
        try {
            Element mapperElement = (Element) document.getElementsByTagName("mapper").item(0);

            // Step 2: Cache all <sql> nodes
            Map<String, Element> sqlFragments = new HashMap<>();
            NodeList children = mapperElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && "sql".equals(child.getNodeName())) {
                    Element sqlElement = (Element) child;
                    String id = sqlElement.getAttribute("id");
                    sqlFragments.put(id, sqlElement);
                }
            }

            // Step 3: Find target statement node and clean it
            Element statementElement = null;
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && statementId.equals(((Element) child).getAttribute("id"))) {
                    statementElement = (Element) child;
                    cleanStatementNode(statementElement);
                    break;
                }
            }

            if (statementElement == null) {
                throw new IllegalArgumentException("Statement not found: " + statementId);
            }

            // Step 4: Create a dynamic mapper XML with cached <sql> nodes and the cleaned statement
            Document newDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element newMapperElement = newDocument.createElement("mapper");
            newDocument.appendChild(newMapperElement);

            for (Element sqlFragment : sqlFragments.values()) {
                Node importedNode = newDocument.importNode(sqlFragment, true);
                newMapperElement.appendChild(importedNode);
            }

            Node importedStatement = newDocument.importNode(statementElement, true);
            newMapperElement.appendChild(importedStatement);
            newMapperElement.setAttribute("namespace", TestkitHelper.getPluginName());

            // Convert the modified XML to a string
            dynamicXmlContent = transformDocumentToString(newDocument);
        } catch (Throwable e) {
            throw new RuntimeException("clean xml error, errorType:" + e.getClass().getName() + ", " + e.getMessage());
        }
        dynamicXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE mapper\n" +
                "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">" + dynamicXmlContent;
        return dynamicXmlContent;
    }

    private static Document parseXml(String xmlContent) throws ParserConfigurationException, IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用外部实体，避免解析时下载远程DTD
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (Throwable e) {
            throw new RuntimeException("xml parse error，errorType:" + e.getClass().getName() + ", " + e.getMessage());
        }
    }

    private static void cleanStatementNode(Element element) {
        // Remove specific attributes
        element.removeAttribute("resultType");
        element.removeAttribute("resultMap");
        element.removeAttribute("parameterType");
        element.removeAttribute("type");
        element.removeAttribute("resultSetType");
        element.removeAttribute("resultSets");


        // Recursively clean child nodes for typeHandler attributes
        cleanTypeHandlerAttributes(element);
    }

    private static void cleanTypeHandlerAttributes(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            element.removeAttribute("typeHandler");
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            cleanTypeHandlerAttributes(children.item(i));
        }
    }

    private static String transformDocumentToString(Document document) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    private static void extractParametersFromElement(Element element, Map<String, Boolean> paramMap) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            try {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element1 = (Element) child;

                    if (element1.getTagName().equals("foreach")) {
                        String collectionAttr = element1.getAttribute("collection");
                        if (collectionAttr.isEmpty()) {
                            continue;
                        }
                        paramMap.put(collectionAttr, true);


                        // Check textual content for ${} and #{}
                        NodeList children2 = element1.getChildNodes();
                        StringBuilder textContent = new StringBuilder();

                        // 遍历当前元素的子节点
                        for (int i2 = 0; i2 < children2.getLength(); i2++) {
                            Node child2 = children2.item(i2);

                            // 只处理直接文本节点
                            if (child2.getNodeType() == Node.TEXT_NODE) {
                                textContent.append(child2.getTextContent());
                            }
                        }

                        String item = element1.getAttribute("item");
                        if (StringUtils.isNotBlank(item)) {
                            extractInlineParametersColl(item, collectionAttr, textContent.toString(), paramMap);
                        }

                        continue;
                    }

                    // Check textual content for ${} and #{}
                    NodeList children2 = element1.getChildNodes();
                    StringBuilder textContent = new StringBuilder();

                    // 遍历当前元素的子节点
                    for (int i2 = 0; i2 < children2.getLength(); i2++) {
                        Node child2 = children2.item(i2);

                        // 只处理直接文本节点
                        if (child2.getNodeType() == Node.TEXT_NODE || child2.getNodeType() == Node.CDATA_SECTION_NODE) {
                            textContent.append(child2.getTextContent());
                        }
                    }

                    extractInlineParameters(textContent.toString(), paramMap);

                    // Check attributes like `test` and `collection`
                    String testAttr = element1.getAttribute("test");
                    if (!testAttr.isEmpty()) {
                        collectExpressionParameters(testAttr, paramMap);
                    }

                    // Recursively process child elements
                    extractParametersFromElement(element1, paramMap);
                }else if(child.getNodeType() == Node.CDATA_SECTION_NODE){
                    extractParametersFromElement((Element) child, paramMap);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println("参数解析异常" + e);
            }
        }
    }

    private static void extractInlineParameters(String text, Map<String, Boolean> paramMap) {
        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_.]*)\\}|\\#\\{([a-zA-Z_][a-zA-Z0-9_.]*)\\}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String fullParam = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            // Check if `.size()` is called in the text indicating it might be an array
            boolean isArray = text.contains(fullParam + ".size()");
            paramMap.put(fullParam, isArray || paramMap.getOrDefault(fullParam, false));
        }
    }

    private static void extractInlineParametersColl(String item, String collection, String text, Map<String, Boolean> paramMap) {
        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_.]*)\\}|\\#\\{([a-zA-Z_][a-zA-Z0-9_.]*)\\}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String fullParam = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            //Check.1 if `.size()` is called in the text indicating it might be an array
            if (!fullParam.startsWith(item + ".")) {
                continue;
            }

            fullParam = collection + fullParam.substring(item.length());
            paramMap.put(fullParam, false);
        }
    }

    private static void collectExpressionParameters(String expression, Map<String, Boolean> paramMap) {
        if (expression == null) {
            return;
        }
        if (expression.endsWith("null")) {
            expression = expression + " ";
        }
        String nexpression = expression.replace(" or ", " ").replace(" and ", " ").replace(".size()", " ").replace(" null ", "");

        Matcher matcher = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_.]*)\\b").matcher(nexpression);
        while (matcher.find()) {
            String fullParam = matcher.group(1);
            if (fullParam.equals("null")) {
                continue;
            }
            if (expression.contains(fullParam + ".size()")) {
                paramMap.put(fullParam, true);
            } else {
                paramMap.put(fullParam, false);
            }
        }
    }


    private static JSONObject generateJsonTemplate(Map<String, Boolean> paramMap) {
        // 使用 TreeMap 自动根据键排序
//        key1 -> false array
        //key1.na1 -> true array
        //输出 {"key1":{"na1":[]}}

        paramMap = new TreeMap<>(paramMap);

        JSONObject rootObject = new JSONObject();
        for (Map.Entry<String, Boolean> entry : paramMap.entrySet()) {
            try {
                String fullPath = entry.getKey();
                boolean isArray = entry.getValue();

                String[] keys = fullPath.split("\\.");

                JSONObject currentObject = rootObject;
                String nowPath = null;
                for (int i = 0; i < keys.length; i++) {
                    String key = keys[i];
                    if (nowPath == null) {
                        nowPath = key;
                    } else {
                        nowPath = nowPath + "." + key;
                    }
                    if (i == keys.length - 1) {
                        // Last key, set value based on whether it's an array

                        if (isArray) {
                            currentObject.put(key, new JSONArray()); // Empty list
                        } else {
                            currentObject.put(key, null); // Default null
                        }
                    } else {
                        Boolean b = paramMap.get(nowPath);
                        if (b != null && b) {
                            JSONArray jsonArray = (JSONArray) currentObject.computeIfAbsent(key, k -> new JSONArray());
                            if (jsonArray.isEmpty()) {
                                jsonArray.add(new JSONObject());
                            }
                            currentObject = (JSONObject) jsonArray.get(0);
                        } else {
                            // Create nested objects as needed
                            currentObject = (JSONObject) currentObject.computeIfAbsent(key, k -> new JSONObject());
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println("初始化mapper参数失败" + e);
            }
        }
        return rootObject;
    }

    public static class FakeDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }


}