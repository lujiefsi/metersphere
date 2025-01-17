package io.metersphere.service;

import io.metersphere.api.exec.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.ApiLocalRunner;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.FixedCapacityUtil;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.ResultDTO;
import io.metersphere.utils.LoggerUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class RemakeReportService {
    @Resource
    @Lazy
    private ApiExecutionQueueService queueService;
    @Resource
    @Lazy
    private TestResultService testResultService;

    public void queueNext(JmeterRunRequestDTO request, String errorMsg) {
        try {
            ResultDTO dto = new ResultDTO();
            BeanUtils.copyBean(dto, request);
            dto.setQueueId(request.getQueueId());
            dto.setTestId(request.getTestId());
            dto.setErrorEnded(true);
            LoggerUtil.info("进入异常结果处理：" + dto.getRunMode() + " 整体处理完成", dto.getReportId());
            // 全局并发队列
            PoolExecBlockingQueueUtil.offer(dto.getReportId());
            LoggerUtil.error("执行异常处理：" + errorMsg, request.getReportId());
            if (StringUtils.isNotEmpty(dto.getQueueId())) {
                queueService.queueNext(dto);
            }
            // 更新测试计划报告
            LoggerUtil.info("Check Processing Test Plan report status.queueId："
                    + dto.getQueueId() + "，runMode:" + dto.getRunMode() + "，testId:" + dto.getTestId(), dto.getReportId());
            queueService.checkTestPlanCaseTestEnd(dto.getTestId(), dto.getRunMode(), dto.getTestPlanReportId());
        } catch (Exception e) {
            LoggerUtil.error("回退报告异常", request.getReportId(), e);
        }finally {
            ApiLocalRunner.clearCache(request.getReportId());
        }
    }

    public void updateReport(JmeterRunRequestDTO request, String errorMsg) {
        ResultDTO dto = new ResultDTO();
        BeanUtils.copyBean(dto, request);
        dto.setQueueId(request.getQueueId());
        dto.setTestId(request.getTestId());
        dto.setErrorEnded(true);
        String consoleMsg = FixedCapacityUtil.getJmeterLogger(dto.getReportId(), true);
        dto.setConsole(consoleMsg + StringUtils.LF + errorMsg);
        testResultService.testEnded(dto);
    }

    public void testEnded(JmeterRunRequestDTO request, String errorMsg) {
        updateReport(request, errorMsg);
        queueNext(request, errorMsg);
    }
}
