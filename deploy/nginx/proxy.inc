# Shared proxy settings for the Spring Boot upstream. Incoming forwarding headers are replaced,
# so a client cannot choose the IP address the app's rate limiter sees. If another proxy or load
# balancer sits in front of this container, add set_real_ip_from for it (ngx_http_realip_module).
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $forwarded_proto;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header Forwarded "";
proxy_read_timeout 60s;
proxy_buffering on;
