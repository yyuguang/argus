import axios from 'axios'

const http = axios.create({
  baseURL: '/',
  timeout: 10000,
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code === 'number') {
      if (payload.code !== 0) {
        return Promise.reject(new Error(payload.message || '请求失败'))
      }
      return payload.data
    }
    return payload
  },
  (error) => Promise.reject(error),
)

export default http
