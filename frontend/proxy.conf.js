module.exports = {
  '/api': {
    target: process.env.GATEWAY_URL || 'http://localhost:8080',
    secure: false,
    changeOrigin: false,
    logLevel: 'warn',
  },
};
