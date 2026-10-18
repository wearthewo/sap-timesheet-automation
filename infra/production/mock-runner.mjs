import {createMockSap} from './server.mjs';
const server=createMockSap({token:process.env.SAP_API_TOKEN,storePath:process.env.SAP_STORE_PATH??'/data/timesheets.json'});
server.listen(Number(process.env.PORT??8090),'0.0.0.0',()=>console.log('Mock SAP ready'));
let stopping=false;
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{
 if(stopping)return;stopping=true;
 server.close(()=>process.exit(0));server.closeIdleConnections();
 setTimeout(()=>{server.closeAllConnections();process.exit(1);},20000).unref();
});
