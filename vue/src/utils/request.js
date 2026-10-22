// 答辩链路-Axios体现：项目通过 axios.create() 统一封装 HTTP 请求；下一步看下面的 baseURL、withCredentials 和响应拦截器。
import axios from 'axios'

const request = axios.create({
    // 答辩链路-接口前缀：页面只写 /stu/find，这里统一加成 /api/stu/find；下一步看 vite.config.js 的 proxy 转发到后端 9090。
    baseURL: '/api',  // 注意！！ 这里是全局统一加上了 '/api' 前缀，也就是说所有接口都会加上'/api'前缀在，页面里面写接口的时候就不要加 '/api'了，否则会出现2个'/api'，类似 '/api/api/user'这样的报错，切记！！！
    timeout: 5000,
    // 答辩链路-Cookie携带：允许浏览器请求时带上 JSESSIONID Cookie；下一步看后端 AdminController/StudentController 用 HttpSession 保存登录信息。
    withCredentials: true
})
let token = '';
// request 拦截器
// 可以自请求发送前对请求做一些处理
// 比如统一加token，对请求参数统一加密
//添加一个请求拦截器
request.interceptors.request.use(function (config) {
    // 答辩链路-请求发出前：如果浏览器 sessionStorage 里有 token，就放进请求头；下一步请求会经过 vite.config.js 的 /api 代理。
    let user = null;
    try {
        user = JSON.parse(window.sessionStorage.getItem('access-user'));
    } catch (error) {
        user = null;
    }
    if (user) {
        token = user.token;
    }
    config.headers.token = token;
    //console.dir(config);
    return config;
}, function (error) {
    // Do something with request error
    console.info("error: ");
    console.info(error);
    return Promise.reject(error);
});

// response 拦截器
// 可以在接口响应后统一处理结果
request.interceptors.response.use(
    response => {
        // 答辩链路-统一响应：后端 Result.success(...) 返回 {code,msg,data}，这里取 response.data；下一步回到具体页面，例如 StuInfo.js 的 then(res)。
        let res = response.data;
        // 如果是返回的文件
        if (response.config.responseType === 'blob') {
            return res
        }
        // 兼容服务端返回的字符串数据
        if (typeof res === 'string') {
            res = res ? JSON.parse(res) : res
        }
        return res;
    },
    error => {
        console.log('err' + error) // for debug
        return Promise.reject(error)
    }
)

// 以request暴露出去
export default request
