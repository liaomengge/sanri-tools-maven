package com.sanri.tools.modules.database.controller;

import com.sanri.tools.modules.database.dtos.DataQueryParam;
import com.sanri.tools.modules.database.dtos.DynamicQueryDto;
import com.sanri.tools.modules.database.dtos.ExportPreviewDto;
import com.sanri.tools.modules.database.dtos.TableDataParam;
import com.sanri.tools.modules.database.dtos.meta.ActualTableName;
import com.sanri.tools.modules.database.service.DataService;
import com.sanri.tools.modules.database.service.JdbcService;
import com.sanri.tools.modules.database.service.TableDataService;
import com.sanri.tools.modules.database.service.TableMarkService;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库数据管理
 */
@RestController
@RequestMapping("/db/data")
public class DataController {
    @Autowired
    private TableMarkService tableMarkService;
    @Autowired
    private TableDataService tableDataService;
    @Autowired
    private JdbcService jdbcService;
    @Autowired
    private DataService dataService;

    /**
     * 获取清除所有业务表数据 sql
     * @return
     */
    @GetMapping("/cleanBizTables")
    public List<String> cleanBizTables(String connName,String catalog,String schemaName,String tagName) throws SQLException, IOException {
        List<ActualTableName> tagTables = tableMarkService.findTagTables(connName,catalog, schemaName, tagName);
        List<String> sqls = new ArrayList<>();
        for (ActualTableName tagTable : tagTables) {
            String tableName = tagTable.getTableName();
            sqls.add("truncate "+tableName);
        }

        return sqls;
    }

    /**
     * 单表随机数据生成
     * @param tableDataParam
     */
    @PostMapping("/singleTableRandomData")
    public void singleTableRandomData(@RequestBody TableDataParam tableDataParam){
        tableDataService.singleTableWriteRandomData(tableDataParam);
    }

    /**
     * 导入数据
     * @param file
     * @throws IOException
     */
    @PostMapping("/import/excel")
    public void importDataFromExcel(MultipartFile file) throws IOException {

    }

    /**
     *  数据预览 , 为避免 sql 语句被编码 , 使用 post 请求数据放在请求体
     * @param dataQueryParam
     * @return
     * @throws IOException
     * @throws SQLException
     * @throws JSQLParserException
     */
    @PostMapping("/exportPreview")
    public ExportPreviewDto exportPreview(@RequestBody DataQueryParam dataQueryParam) throws IOException, SQLException, JSQLParserException {
        DynamicQueryDto dynamicQueryDto = dataService.exportPreview(dataQueryParam);
        String connName = dataQueryParam.getConnName();
        String sql = dataQueryParam.getFirstSql();

        // 数据总数查询
        String countSql = "select count(*) from (" + sql + ") b";
        Long executeQuery = jdbcService.executeQuery(connName, countSql, new ScalarHandler<Long>(1));
        ExportPreviewDto exportPreviewDto = new ExportPreviewDto(dynamicQueryDto, executeQuery);
        return exportPreviewDto;
    }

    /**
     * 导出数据为 csv 格式,导出进度会写入指定 key , 可查询导出进度
     * 当数据量过大时使用多线程导出
     * @param connName
     * @param sql
     * @return
     */
    @PostMapping("/exportData")
    public String exportData(@RequestBody DataQueryParam dataQueryParam) throws JSQLParserException, SQLException, IOException {
        String fileRelativePath = dataService.exportLowMemoryMutiProcessor(dataQueryParam);
        return fileRelativePath;
    }

    /**
     * 执行查询 sql
     * @param dataQueryParam
     * @return
     * @throws IOException
     * @throws SQLException
     */
    @PostMapping("/executeQuery")
    public List<DynamicQueryDto> executeQuery(@RequestBody DataQueryParam dataQueryParam) throws IOException, SQLException {
        List<DynamicQueryDto> dynamicQueryDtos = jdbcService.executeDynamicQuery(dataQueryParam.getConnName(), dataQueryParam.getSqls());
        return dynamicQueryDtos;
    }

    /**
     * 执行更新操作, 包含 ddl
     * @param dataQueryParam
     * @return
     * @throws SQLException
     */
    @PostMapping("/executeUpdate")
    public List<Integer> executeUpdate(@RequestBody DataQueryParam dataQueryParam) throws SQLException, IOException {
        List<Integer> updates = jdbcService.executeUpdate(dataQueryParam.getConnName(), dataQueryParam.getSqls());
        return updates;
    }
}
