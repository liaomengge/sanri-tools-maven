package com.sanri.tools.configs;

import com.sanri.tools.modules.core.exception.BusinessException;
import com.sanri.tools.modules.core.exception.RemoteException;
import com.sanri.tools.modules.core.exception.SystemMessage;
import com.sanri.tools.modules.core.dtos.ResponseDto;
import com.sanri.tools.modules.core.exception.ToolException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${sanri.webui.package.prefix:com.sanri.tools}")
    protected String packagePrefix;

    /**
     * 处理业务异常
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseDto businessException(BusinessException e){
        printLocalStackTrack(e);
        return e.getResponseDto();
    }

    @ExceptionHandler(RemoteException.class)
    public ResponseDto remoteException(RemoteException e){
        ResponseDto parentResult = e.getParent().getResponseDto();
        ResponseDto resultEntity = e.getResponseDto();
        //返回给前端的是业务错误，但是需要在控制台把远程调用异常给打印出来
        log.error(parentResult.getCode()+":"+parentResult.getMessage()
                +" \n -| "+resultEntity.getCode()+":"+resultEntity.getMessage());

        printLocalStackTrack(e);

        //合并两个结果集返回
        ResponseDto merge = ResponseDto.err(parentResult.getCode())
                .message(parentResult.getMessage()+" \n  |- "+resultEntity.getCode()+":"+resultEntity.getMessage());
        return merge;
    }

    /**
     * 打印只涉及到项目类调用的异常堆栈
     * @param e
     */
    private void printLocalStackTrack(BusinessException e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        List<StackTraceElement> localStackTrack = new ArrayList<>();
        StringBuffer showMessage = new StringBuffer();
        if (ArrayUtils.isNotEmpty(stackTrace)) {
            for (StackTraceElement stackTraceElement : stackTrace) {
                String className = stackTraceElement.getClassName();
                int lineNumber = stackTraceElement.getLineNumber();
                if (className.startsWith(packagePrefix)) {
                    localStackTrack.add(stackTraceElement);
                    showMessage.append(className + "(" + lineNumber + ")\n");
                }
            }
            log.error("业务异常:" + e.getMessage() + "\n" + showMessage);
        } else {
            log.error("业务异常,没有调用栈 " + e.getMessage());
        }
    }

    @ExceptionHandler(value = BindException.class)
    public ResponseDto bindException(BindException ex) {
        // ex.getFieldError():随机返回一个对象属性的异常信息。如果要一次性返回所有对象属性异常信息，则调用ex.getAllErrors()
        FieldError fieldError = ex.getFieldError();
        assert fieldError != null;
        return SystemMessage.ARGS_ERROR.exception(fieldError.getField(),fieldError.getRejectedValue()).getResponseDto();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseDto methodArgumentNotValidException(MethodArgumentNotValidException e){
        FieldError fieldError = e.getBindingResult().getFieldError();
        assert fieldError != null;
        return SystemMessage.ARGS_ERROR.exception(fieldError.getField(),fieldError.getRejectedValue(),fieldError.getDefaultMessage()).getResponseDto();
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseDto constraintViolationException(ConstraintViolationException ex){
        ConstraintViolation<?> constraintViolation = ex.getConstraintViolations().iterator().next();
        String message = constraintViolation.getMessage();
        return SystemMessage.ARGS_ERROR2.exception(message).getResponseDto();
    }

    /**
     * 异常处理，可以绑定多个
     * @return
     */
    @ExceptionHandler(Exception.class)
    public ResponseDto otherException(Exception e){
        log.error(e.getMessage(),e);
        return ResponseDto.err(e.getClass().getSimpleName()).message(e.getMessage());
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseDto fileNotFound(FileNotFoundException e){
        log.error(e.getMessage(),e);
        return SystemMessage.NETWORK_ERROR.result();
    }

    @ExceptionHandler(IOException.class)
    public ResponseDto ioException(IOException e){
        log.error(e.getMessage(),e);
        return SystemMessage.NETWORK_ERROR.result();
    }

    @ExceptionHandler(ToolException.class)
    public ResponseDto toolException(ToolException e){
        BusinessException businessException = BusinessException.create(e.getMessage());
        return businessException.getResponseDto();
    }
}
