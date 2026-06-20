package com.imooc.msseckill.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.constant.RedisKeyConstant;
import com.imooc.commons.exception.ParameterException;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.pojo.SeckillVouchers;
import com.imooc.commons.model.pojo.VoucherOrders;
import com.imooc.commons.model.vo.SignInDinerInfo;
import com.imooc.commons.utils.AssertUtil;
import com.imooc.commons.utils.ResultInfoUtil;
import com.imooc.msseckill.mapper.SeckillVouchersMapper;
import com.imooc.msseckill.mapper.VoucherOrdersMapper;
import com.imooc.msseckill.model.RedisLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.reflections.Reflections.log;

/**
 * 秒杀业务逻辑层
 */
@Service
public class SeckillService {

    @Resource
    private SeckillVouchersMapper seckillVouchersMapper;
    @Resource
    private VoucherOrdersMapper voucherOrdersMapper;
    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private DefaultRedisScript stockScript;
    @Resource
    private RedisLock redisLock;
    @Resource
    private RedissonClient redissonClient;

    //抢购代金券
    //超卖问题是怎么产生的，因为在redis里，redis查询和redis扣库存，分两步完成，在多并发情况下很容易出现超卖问题，这时候就需要通过lua脚本，把这两步合并，变成一个原子操作
    @Transactional(rollbackFor = Exception.class)
    public ResultInfo doSeckill(Integer voucherId, String accessToken, String path) {
        // 基本参数校验
        AssertUtil.isTrue(voucherId == null || voucherId < 0, "请选择需要抢购的代金券");
        AssertUtil.isNotEmpty(accessToken, "请登录");

        String key = RedisKeyConstant.seckill_vouchers.getKey() + voucherId;
        Map<String, Object> map = redisTemplate.opsForHash().entries(key);
        SeckillVouchers seckillVouchers = BeanUtil.mapToBean(map, SeckillVouchers.class, true, null);

        // 判断是否开始、结束
        Date now = new Date();
        AssertUtil.isTrue(now.before(seckillVouchers.getStartTime()), "该抢购还未开始");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "该抢购已结束");
        AssertUtil.isTrue(seckillVouchers.getAmount() < 1, "该券已经卖完了");

        // 获取登录用户信息
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            resultInfo.setPath(path);
            return resultInfo;
        }

        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);

        // 判断用户是否已抢到
        VoucherOrders order = voucherOrdersMapper.findDinerOrder(dinerInfo.getId(),
                seckillVouchers.getFkVoucherId());
        AssertUtil.isTrue(order != null, "该用户已抢到该代金券，无需再抢");

        String lockName = RedisKeyConstant.lock_key.getKey() + dinerInfo.getId() + ":" + voucherId;
        RLock lock = redissonClient.getLock(lockName);
        boolean isLocked = false;

        try {
            // 1. 尝试获取锁
            isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!isLocked) {
                return ResultInfoUtil.buildError(0, "抢购人数过多，请重试", path);
            }

            // 2. 先原子扣库存
            List<String> keys = Arrays.asList(key, "amount");
            Long leftStock = (Long) redisTemplate.execute(stockScript, keys);
            if (leftStock == null || leftStock < 1) {
                return ResultInfoUtil.buildError(0, "该券已经卖完", path);
            }

            // 3. 再创建订单
            VoucherOrders voucherOrders = new VoucherOrders();
            voucherOrders.setFkDinerId(dinerInfo.getId());
            voucherOrders.setFkVoucherId(seckillVouchers.getFkVoucherId());
            String orderNo = IdUtil.getSnowflake(1, 1).nextIdStr();
            voucherOrders.setOrderNo(orderNo);
            voucherOrders.setOrderType(1);
            voucherOrders.setStatus(0);
            long count = voucherOrdersMapper.save(voucherOrders);
            AssertUtil.isTrue(count == 0, "用户抢购失败");

            return ResultInfoUtil.buildSuccess(path, "抢购成功");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultInfoUtil.buildError(0, "系统繁忙，请重试", path);
        } catch (Exception e) {
            // 其他所有异常
            log.error("秒杀异常", e);
            return ResultInfoUtil.buildError(0, "抢购失败，请重试", path);
        } finally {
            // 只释放锁，不要有return！
            if (isLocked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.error("释放锁异常", e);
                }
            }
        }
    }
    //添加需要抢购的代金券
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVouchers(SeckillVouchers seckillVouchers) {
        // 非空校验
        AssertUtil.isTrue(seckillVouchers.getFkVoucherId() == null, "请选择需要抢购的代金券");
        AssertUtil.isTrue(seckillVouchers.getAmount() == 0, "请输入抢购总数量");
        Date now = new Date();
        AssertUtil.isNotNull(seckillVouchers.getStartTime(), "请输入开始时间");
        // 生产环境下面一行代码需放行，这里注释方便测试
        // AssertUtil.isTrue(now.after(seckillVouchers.getStartTime()), "开始时间不能早于当前时间");
        AssertUtil.isNotNull(seckillVouchers.getEndTime(), "请输入结束时间");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "结束时间不能早于当前时间");
        AssertUtil.isTrue(seckillVouchers.getStartTime().after(seckillVouchers.getEndTime()), "开始时间不能晚于结束时间");

        // 注释原始的走 关系型数据库 的流程
        // 验证数据库中是否已经存在该券的秒杀活动
        // SeckillVouchers seckillVouchersFromDb = seckillVouchersMapper.selectVoucher(seckillVouchers.getFkVoucherId());
        // AssertUtil.isTrue(seckillVouchersFromDb != null, "该券已经拥有了抢购活动");
        // 插入数据库
        // seckillVouchersMapper.save(seckillVouchers);

        //采用redis实现
        String key = RedisKeyConstant.seckill_vouchers.getKey() + seckillVouchers.getFkVoucherId();
        //验证redis中是否已经存在该券的秒杀活动
       Map<String,Object> map= redisTemplate.opsForHash().entries(key);
       AssertUtil.isTrue(!map.isEmpty()&&(int)map.get("amount")>0,"该券已经拥有了抢购活动");
       //插入redis
        seckillVouchers.setIsValid(1);
        seckillVouchers.setCreateDate( now);
        seckillVouchers.setUpdateDate( now);
        BeanUtil.beanToMap(seckillVouchers);
        redisTemplate.opsForHash().putAll(key, map);

    }

}