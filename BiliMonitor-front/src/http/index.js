// 抽取 axios 请求
import axios from "axios";

const http = axios.create({
    baseURL: "http://localhost:8080",
    timeout: 3000,
    headers: {
        'X-Custom-Header': 'foobar'
    }
});


// 添加请求拦截器
http.interceptors.request.use(function (config) {
    // 在发送请求之前做些什么
    return config;
}, function (error) {
    // 对请求错误做些什么
    return Promise.reject(error);
});

// 添加响应拦截器
http.interceptors.response.use(function (response) {
    // 2xx 范围内的状态码都会触发该函数。
    // 对响应数据做点什么
    // 拿到服务器真正的响应
    return response.data;
}, function (error) {
    // 超出 2xx 范围的状态码都会触发该函数。
    // 对响应错误做点什么
    return Promise.reject(error);
});

// 导出
export default http;

