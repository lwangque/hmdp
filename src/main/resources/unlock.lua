--比较是否是自己的锁
if(redis.call("get",KEYS[1])==ARGV[1])then
    --是则可以删除锁
    redis.call("del",KEYS[1])
end
return 0