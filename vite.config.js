import { resolve } from 'path'

const scalaVersion = '2.13.6'
// const scalaVersion = '3.0.0-RC1'

// https://vitejs.dev/config/
export default ({ mode }) => {
//  const mainJS = `frontend/target/scala-${scalaVersion}/frontend-${mode === 'production' ? 'opt' : 'fastopt'}/main.js`
//  console.log('mainJS', mainJS)
  return {
    publicDir: './frontend/src/main/static/public',
    resolve: {
      alias: {
        'stylesheets': resolve(__dirname, './frontend/src/main/static/stylesheets'),
      }
    },
    server: {
      port: 3000,
      cors: {
        "origin": "*",
        "methods": "GET,HEAD,POST,DELETE",
        "preflightContinue": false,
        "optionsSuccessStatus": 204
      }
    }
  }
}
