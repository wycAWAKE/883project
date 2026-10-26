import axios from 'axios'

const request = axios.create({
  // The Vite proxy strips this prefix once. Module calls use /faq, not /api/faq.
  baseURL: '/api',
  timeout: 5000,
  withCredentials: true,
})

request.interceptors.response.use(response => {
  const data = response.data
  if (response.config.responseType === 'blob') return data
  return typeof data === 'string' && data ? JSON.parse(data) : data
})

export default request
