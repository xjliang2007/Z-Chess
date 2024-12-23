package com.isahl.chess.player.api.controller;

import com.isahl.chess.player.api.model.LcApiListResponse;
import com.isahl.chess.player.api.model.LcApiTokenDO;
import com.isahl.chess.player.api.model.RpaAuthDo;
import com.isahl.chess.player.api.model.RpaTaskDO;
import com.isahl.chess.player.api.model.RpaTaskMessageDO;
import com.isahl.chess.player.api.service.AliothApiService;
import com.isahl.chess.player.api.service.BiddingRpaScheduleService;
import com.isahl.chess.player.api.service.LcApiService;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xiaojiang.lxj at 2024-05-10 14:54.
 */
@RestController
@RequestMapping("test")
public class TestController {

    private ExecutorService executorService = new ThreadPoolExecutor(10, 100,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(50));;

    @Autowired
    private AliothApiService aliothApiService;

    @Autowired
    private LcApiService lcApiService;

    @Autowired
    private BiddingRpaScheduleService biddingRpaScheduleService;

    @GetMapping("get-auth-info")
    public List<RpaAuthDo> getAuthInfos(){
        return aliothApiService.fetchAuthInfos();
    }

    @GetMapping("get-task")
    public List<RpaTaskDO> fetchTask(){
        return aliothApiService.fetchUnfinishedTaskList();
    }

    @GetMapping("update-task-status")
    public Object updateTaskStatus(@RequestParam(name = "taskId") String taskId, @RequestParam(name = "status") String status){
        for(String tid : taskId.split(",")){
            RpaTaskMessageDO message = new RpaTaskMessageDO();
            message.setTaskId(Long.parseLong(tid));
            message.setStatus(status);
            aliothApiService.updateTask(message);
        }

        return "OK";
    }

    @GetMapping("trigger-bidding-task")
    public Object triggerBiddingTask(@RequestParam(name = "taskId",required = false) Long taskId){
        Long tid;
        if(ObjectUtils.isEmpty(taskId)){
            tid = null;
        }else{
            tid = taskId;
        }
        executorService.submit(() -> biddingRpaScheduleService.queryAndBooking(tid));
        return "OK";
    }

    @GetMapping("cancel-bidding-task")
    public Object cancelBiddingTask(@RequestParam(name = "taskId",required = false) Long taskId){
        Long tid;
        if(ObjectUtils.isEmpty(taskId)){
            tid = null;
        }else{
            tid = taskId;
        }
        executorService.submit(() -> biddingRpaScheduleService.cancelBooking(tid));
        return "OK";
    }

    @GetMapping("get-lc-api-token")
    public Object getLcApiToken(){
        return aliothApiService.fetchLcAppTokenList();
    }

    @GetMapping("get-lc-order-list")
    public Object getLcOrderList(
        @RequestParam(name = "appToken") String appToken,
        @RequestParam(name = "appKey") String appKey,
        @RequestParam(name = "page",defaultValue = "1")Integer page,
        @RequestParam(name = "pageSize",defaultValue = "1") Integer pageSize,
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        return lcApiService.fetchOrderList(appToken,appKey,page,pageSize,createFrom,createTo);
    }

    @GetMapping("save-lc-order-list")
    public Object getAndSaveLcOrderList(
        @RequestParam(name = "appToken") String appToken,
        @RequestParam(name = "appKey") String appKey,
        @RequestParam(name = "page",defaultValue = "1")Integer page,
        @RequestParam(name = "pageSize",defaultValue = "1") Integer pageSize,
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        LcApiListResponse response = lcApiService.fetchOrderList(appToken,appKey,page,pageSize,createFrom,createTo);
        lcApiService.saveOrderList(response.getData());
        return "OK";
    }

    @GetMapping("save-lc-order-list-v2")
    public Object getAndSaveLcOrderListV2(
        @RequestParam(name = "appToken") String appToken,
        @RequestParam(name = "appKey") String appKey,
        @RequestParam(name = "page",defaultValue = "1")Integer page,
        @RequestParam(name = "pageSize",defaultValue = "1") Integer pageSize,
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        LcApiListResponse response = lcApiService.fetchOrderList(appToken,appKey,page,pageSize,createFrom,createTo);
        lcApiService.saveOrderListV2(response.getData());
        return "OK";
    }

    @GetMapping("import-lc-order-list")
    public Object importLcOrderList(
        @RequestParam(name = "appToken") String appToken,
        @RequestParam(name = "appKey") String appKey,
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        executorService.submit(() -> lcApiService.importOrderListFromLc(appToken,appKey,createFrom,createTo));
        return "OK";
    }

    @GetMapping("import-lc-order-list-v2")
    public Object importLcOrderListV2(
        @RequestParam(name = "appToken") String appToken,
        @RequestParam(name = "appKey") String appKey,
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        executorService.submit(() -> lcApiService.importOrderListFromLcV2(appToken,appKey,createFrom,createTo));
        return "OK";
    }

    /**
     * v1版本，订单数据通过noco api写入数据库，很慢
     *
     * @return
     */
    @GetMapping("import-lc-order-list-all")
    public Object importLcOrderListAll(
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        List<LcApiTokenDO> tokenList = aliothApiService.fetchLcAppTokenList();
        if(CollectionUtils.isEmpty(tokenList)){
            return "appToken list is empty, please check!";
        }
        for(LcApiTokenDO tokenDO : tokenList){
            executorService.submit(() -> lcApiService.importOrderListFromLc(tokenDO.getApp_token(),tokenDO.getApp_key(),createFrom,createTo));
        }
        return "OK";
    }

    /**
     * v2版本，订单数据直接写数据库，比较块
     *
     * @return
     */
    @GetMapping("import-lc-order-list-all-v2")
    public Object importLcOrderListAllV2(
        @RequestParam(name = "createFrom",required = false) String createFrom,
        @RequestParam(name = "createTo",required = false) String createTo
    ){
        List<LcApiTokenDO> tokenList = aliothApiService.fetchLcAppTokenList();
        if(CollectionUtils.isEmpty(tokenList)){
            return "appToken list is empty, please check!";
        }
        for(LcApiTokenDO tokenDO : tokenList){
            executorService.submit(() -> lcApiService.importOrderListFromLcV2(tokenDO.getApp_token(),tokenDO.getApp_key(),createFrom,createTo));
        }
        return "OK";
    }
}
