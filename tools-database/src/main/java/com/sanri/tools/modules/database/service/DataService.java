package com.sanri.tools.modules.database.service;

import com.sanri.tools.modules.core.service.file.FileManager;
import com.sanri.tools.modules.database.dtos.DataQueryParam;
import com.sanri.tools.modules.database.dtos.DynamicQueryDto;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Service
@Slf4j
public class DataService {
    @Autowired
    private JdbcService jdbcService;

    // jsqlparser 解析
    CCJSqlParserManager parserManager = new CCJSqlParserManager();

    @Autowired
    private FileManager fileManager;

    /**
     * 数据导出预览
     * @param dataQueryParam
     * @return
     * @throws JSQLParserException
     * @throws IOException
     * @throws SQLException
     */
    public DynamicQueryDto exportPreview(DataQueryParam dataQueryParam) throws JSQLParserException, IOException, SQLException {
        String connName = dataQueryParam.getConnName();
        String sql = dataQueryParam.getFirstSql();
        // sql 解析,加上 limit 限制条数
        Select select = (Select) parserManager.parse(new StringReader(sql));
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Limit limit = new Limit();
        limit.setOffset(0);
        limit.setRowCount(15);
        plainSelect.setLimit(limit);

        List<DynamicQueryDto> dynamicQueryDtos = jdbcService.executeDynamicQuery(connName, Collections.singletonList(select.toString()));
        return dynamicQueryDtos.get(0);
    }

    /**
     * 单线程导出数据
     * @param dataQueryParam
     * @return
     */
    public String exportSingleProcessor(DataQueryParam dataQueryParam) throws IOException, SQLException {
        String connName = dataQueryParam.getConnName();
        String sql = dataQueryParam.getFirstSql();

        File exportDir = fileManager.mkTmpDir("database/data/export/" + dataQueryParam.getTraceId());

        List<DynamicQueryDto> dynamicQueryDtos = jdbcService.executeDynamicQuery(connName,Collections.singletonList(sql));
        DynamicQueryDto dynamicQueryDto = dynamicQueryDtos.get(0);
        File excelPartFile = new File(exportDir, dataQueryParam.getTraceId()+ ".xlsx");
        log.info("Excel 文件 :{}",excelPartFile.getName());

        Workbook workbook = new SXSSFWorkbook(1000);
        Sheet sheet = workbook.createSheet(connName);
        FileOutputStream fileOutputStream = new FileOutputStream(excelPartFile);
        fillExcelSheet(dynamicQueryDto,sheet);
        workbook.write(fileOutputStream);

        Path path = fileManager.relativePath(exportDir.toPath());
        return path.toString();
    }

    // 单线程导出最大数据量
    static final int exportPerLimit = 10000;

    // 数据导出线程池 私用
    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1,10,0, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(100));
    /**
     * 多线程导出数据
     * @param dataQueryParam
     * @throws IOException
     * @throws SQLException
     * @return
     */
    public String exportLowMemoryMutiProcessor(DataQueryParam dataQueryParam) throws IOException, SQLException, JSQLParserException {
        String connName = dataQueryParam.getConnName();
        String sql = dataQueryParam.getFirstSql();

        // 查询数据总数
        String countSql = "select count(*) from (" + sql + ") b";
        Long dataCount = jdbcService.executeQuery(connName, countSql, new ScalarHandler<Long>(1));

        if(dataCount < exportPerLimit){
            return exportSingleProcessor(dataQueryParam);
        }

        //计算线程数
        final int threadCount = (int) ((dataCount - 1) / exportPerLimit + 1);

        log.info("启用多线程进度导出:{}",dataQueryParam.getTraceId());

        //创建临时目录
        File exportDir = fileManager.mkTmpDir("database/data/export/"+dataQueryParam.getTraceId());
        log.info("临时文件将输出到此目录:{}",exportDir);

        Select select = (Select) parserManager.parse(new StringReader(sql));
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        //多线程导出; 分每批 10 万 生成多个 Excel,每个 Excel 生成后;  释放到临时文件中,释放内存,最后将整个目录使用 zip 打包
        for (int i = 0; i < threadCount; i++) {
            int currentBatch = i;
            final long begin = currentBatch * exportPerLimit;
            long end = (currentBatch + 1) * exportPerLimit;
            if(end > dataCount){
                end = dataCount;
            }
            final long  finalEnd = end;
            Limit limit = new Limit();
            limit.setOffset(begin);
            limit.setRowCount(end);
            plainSelect.setLimit(limit);
            final String currentSql = select.toString();

            threadPoolExecutor.submit(new Thread() {
                @Override
                public void run() {
                    FileOutputStream fileOutputStream = null;
                    try {
                        List<DynamicQueryDto> dynamicQueryDtos = jdbcService.executeDynamicQuery(connName,Collections.singletonList(currentSql));
                        DynamicQueryDto dynamicQueryDto = dynamicQueryDtos.get(0);
                        File excelPartFile = new File(exportDir, dataQueryParam.getTraceId()+"_" + begin + "~" + finalEnd + ".xlsx");
                        log.info("Excel 部分文件 :{}",excelPartFile.getName());

                        Workbook workbook = new SXSSFWorkbook(1000);
                        Sheet sheet = workbook.createSheet(connName +  "_" + begin + "~" + finalEnd);
                        fileOutputStream = new FileOutputStream(excelPartFile);
                        fillExcelSheet(dynamicQueryDto,sheet);
                        workbook.write(fileOutputStream);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        IOUtils.closeQuietly(fileOutputStream);
                    }
                }
            });

        }

        Path path = fileManager.relativePath(exportDir.toPath());
        return path.toString();
    }

    /**
     * 填充 excel sheet 页
     * @param session
     * @param sqlExecuteResult
     * @param sheet
     */
    public static final float BASE_HEIGHT_1_PX = 15.625f;
    private void fillExcelSheet( DynamicQueryDto sqlExecuteResult, Sheet sheet) {
        Row headRow = sheet.createRow(0);
        headRow.setHeight((short)(30 * BASE_HEIGHT_1_PX));
        //创建标题列
        List<DynamicQueryDto.Header> headers = sqlExecuteResult.getHeaders();

        for (int i = 0; i < headers.size(); i++) {
            DynamicQueryDto.Header header = headers.get(i);
            Cell headCell = headRow.createCell(i);
            headCell.setCellValue(header.getColumnName());
            headCell.setCellType(Cell.CELL_TYPE_STRING);
        }
        //创建数据列
        List<List<Object>> rows = sqlExecuteResult.getRows();

        for (int i = 0; i < rows.size(); i++) {
            //设置进度
            List<Object> objects = rows.get(i);
            Row dataRow = sheet.createRow(i + 1);
            for (int j = 0; j < objects.size(); j++) {
                DynamicQueryDto.Header colTypeHeader = headers.get(j);
                String colType = colTypeHeader.getTypeName();

                Cell cell = dataRow.createCell(j);
                Object value = objects.get(j);

                if(value == null){
                    // 空值
                    cell.setCellType(Cell.CELL_TYPE_BLANK);
                    continue;
                }
                if("char".equalsIgnoreCase(colType) || "varchar".equalsIgnoreCase(colType)) {
                    cell.setCellValue(ObjectUtils.toString(value));
                    cell.setCellType(Cell.CELL_TYPE_STRING);
                }else if ("datetime".equalsIgnoreCase(colType)){
                    cell.setCellType(Cell.CELL_TYPE_STRING);
                    Timestamp timestamp = (Timestamp) value;
                    long time = timestamp.getTime();
                    String format = DateFormatUtils.ISO_DATE_FORMAT.format(time);
                    cell.setCellValue(format);
                }else if("int".equalsIgnoreCase(colType) || "decimal".equalsIgnoreCase(colType)){
                    cell.setCellType(Cell.CELL_TYPE_NUMERIC);
                    cell.setCellValue(NumberUtils.toLong(ObjectUtils.toString(value)));
                }else if ("date".equalsIgnoreCase(colType)){
                    cell.setCellType(Cell.CELL_TYPE_STRING);
                    cell.setCellValue(ObjectUtils.toString(value));
                }else if("TINYINT".equalsIgnoreCase(colType)){
                    cell.setCellType(Cell.CELL_TYPE_STRING);
                    cell.setCellValue(ObjectUtils.toString(value));
                }else {
                    log.error("不支持的数据库类型,需要添加类型支持:{},value:{}",colType,value);
                }
            }
        }

        //设置列宽; 自动列宽
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
