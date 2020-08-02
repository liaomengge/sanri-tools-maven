package com.sanri.tools.modules.mybatis.service;

import com.alibaba.fastjson.JSON;
import com.sanri.tools.modules.core.dtos.PluginDto;
import com.sanri.tools.modules.core.service.classloader.ClassloaderService;
import com.sanri.tools.modules.core.service.classloader.ExtendClassloader;
import com.sanri.tools.modules.core.service.file.FileManager;
import com.sanri.tools.modules.core.service.plugin.PluginManager;
import com.sanri.tools.modules.mybatis.dtos.BoundSqlParam;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MybatisService {
    @Autowired
    private FileManager fileManager;

    @Autowired
    private PluginManager pluginManager;

    @Autowired
    private ClassloaderService classloaderService;

    public static final String module = "mybatis";

    // projectName => Configuration
    private Map<String, Configuration> projectConfigurationMap = new ConcurrentHashMap<>();

    /**
     * 上传一个新的 mapper 文件到指定项目
     * @param project
     * @param file
     */
    public void newProjectFile(String project,String classloaderName, MultipartFile file) throws IOException {
        File projectDir = fileManager.mkTmpDir(module + "/" + project);
        file.transferTo(new File(projectDir,file.getOriginalFilename()));

        loadMapperFile(project,file.getOriginalFilename(),classloaderName);
    }

    /**
     * 加载指定项目 mapper 文件
     * @param project
     * @param fileName
     * @throws IOException
     */
    public void loadMapperFile(String project,String fileName,String classloaderName) throws IOException {
        // 得到类加载器
        ClassLoader classloader = classloaderService.getClassloader(classloaderName);
        Resources.setDefaultClassLoader(classloader);

        Resource resource = fileManager.relativeResource(module + "/" + project + "/" + fileName);
        InputStream inputStream = resource.getInputStream();
        Configuration configuration = projectConfigurationMap.computeIfAbsent(project, k -> new Configuration());
        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource.getFilename(),new HashMap<>());
        mapperParser.parse();
        inputStream.close();

        Resources.setDefaultClassLoader(ClassLoader.getSystemClassLoader());
    }

    /**
     * 获取当前项目所有可执行的 sqlId
     * @param project
     * @return
     */
    public List<String> statementIds(String project){
        Configuration configuration = projectConfigurationMap.computeIfAbsent(project, k -> new Configuration());
        Collection<MappedStatement> mappedStatements = configuration.getMappedStatements();
        List<String> collect = mappedStatements.stream().map(MappedStatement::getId).collect(Collectors.toList());
        return collect;
    }

    /**
     * 获取绑定的 sql 语句
     * @return
     */
    public String boundSql(BoundSqlParam boundSqlParam) throws ClassNotFoundException {
        String project = boundSqlParam.getProject();
        String statementId = boundSqlParam.getStatementId();
        Configuration configuration = projectConfigurationMap.computeIfAbsent(project, k -> new Configuration());
        MappedStatement mappedStatement = configuration.getMappedStatement(statementId);

        String classloaderName = boundSqlParam.getClassloaderName();
        ClassLoader classloader = classloaderService.getClassloader(classloaderName);
        if (classloader == null)classloader = ClassLoader.getSystemClassLoader();

        String arg = boundSqlParam.getArg();
        String className = boundSqlParam.getClassName();
        Class<?> clazz = classloader.loadClass(className);
        BoundSql boundSql = null;
        if (clazz.isPrimitive() || clazz == String.class){
            boundSql= mappedStatement.getBoundSql(arg);
        }else {
            Object parameterObject = JSON.parseObject(arg, clazz);
            boundSql = mappedStatement.getBoundSql(parameterObject);
        }

        return boundSql.getSql();
    }

    /**
     * 当重启项目后,需要手动重新加载所有的 mybatis 配置文件
     * 不做启动加载是因为会延长加载时间,但这个功能用到的可能性并不是太高
     * TODO classloader
     */
    public void reload(){
        projectConfigurationMap.clear();
        File moduleDir = fileManager.mkTmpDir(module);
        File[] files = moduleDir.listFiles();
        for (File project : files) {
            String projectName = project.getName();
            File[] mapperFiles = project.listFiles();
            for (File mapperFile : mapperFiles) {
                try {
                    loadMapperFile(projectName,mapperFile.getName(),null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @PostConstruct
    public void register(){
        pluginManager.register(PluginDto.builder().module(module).name("main").build());
    }
}