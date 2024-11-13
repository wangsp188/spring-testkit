package com.nb.tools.mybatis_sql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
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
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.util.logging.Logger;

public class SqlGenerator {

    public static String generateSql(String xmlContent, String statementId,boolean prepared, JSONObject parameters) throws Exception {
        xmlContent = cleanXml(xmlContent, statementId);
        // Step 1: Initialize MyBatis Configuration and parse XML content
        Configuration configuration = new Configuration();
        // Initialize an environment (we don't need actual data source for SQL generation)
        Environment environment = new Environment("No-Bug", new JdbcTransactionFactory(), new FakeDataSource());
        configuration.setEnvironment(environment);
        // 使用正则表达式移除typeHandler属性 (注意：正则可能影响其他配置，请具体分析XML结构）
        // Step 2: Parse the mapper XML and build a MappedStatement
        XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(new StringReader(xmlContent), configuration,"No-Bug", configuration.getSqlFragments());
        mapperBuilder.parse(); // Parses the XML and adds statements to configuration

        // Step 3: Get the MappedStatement by statementId
        MappedStatement mappedStatement = configuration.getMappedStatement(statementId);
        DynamicSqlSource sqlSource = (DynamicSqlSource) mappedStatement.getSqlSource();
        BoundSql boundSql = sqlSource.getBoundSql(parameters);

        // Step 4: Prepare SQL with parameters
        String sql = boundSql.getSql();
        if(prepared){
            return sql;
        }
        // Step 5: Generate final SQL by replacing placeholders with actual values
        return buildCompleteSql(sql, boundSql, parameters);
    }

    private static String buildCompleteSql(String sql, BoundSql boundSql, JSONObject parameters) {
        // Iterate over parameters and replace "?" placeholders
        StringBuilder finalSql = new StringBuilder();
        String[] sqlParts = sql.split("\\?");
        int paramIndex = 0;

        for (String part : sqlParts) {
            finalSql.append(part);
            if (paramIndex < boundSql.getParameterMappings().size()) {
                String paramName = boundSql.getParameterMappings().get(paramIndex).getProperty();
                Object paramValue = getParameterValue(paramName, parameters);
                finalSql.append(paramValue != null ? paramValue : "NULL");
                paramIndex++;
            }
        }
        return finalSql.toString();
    }

    private static Object getParameterValue(String paramName, JSONObject parameters) {
        // Retrieve the parameter value from JSON, supporting nested JSON and arrays
        if (paramName.contains(".")) {
            String[] nestedKeys = paramName.split("\\.");
            Object value = parameters;
            for (String key : nestedKeys) {
                if (value instanceof JSONObject) {
                    value = ((JSONObject) value).get(key);
                } else if (value instanceof JSONArray && key.matches("\\d+")) {
                    value = ((JSONArray) value).get(Integer.parseInt(key));
                }
            }
            return value;
        }
        return parameters.get(paramName);
    }

    private static @NotNull String cleanXml(String xmlContent, String statementId) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        // Step 1: Parse XML and find <mapper> node
        Document document = parseXml(xmlContent);
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
        newMapperElement.setAttribute("namespace","No-Bug");

        // Convert the modified XML to a string
        String dynamicXmlContent = transformDocumentToString(newDocument);
        dynamicXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE mapper\n" +
                "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">" + dynamicXmlContent;
        return dynamicXmlContent;
    }

    private static Document parseXml(String xmlContent) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xmlContent)));
    }

    private static void cleanStatementNode(Element element) {
        // Remove specific attributes
        element.removeAttribute("resultType");
        element.removeAttribute("resultMap");
        element.removeAttribute("parameterType");
        element.removeAttribute("type");

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



    public static   class FakeDataSource implements DataSource {
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