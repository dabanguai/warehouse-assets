<script setup lang="ts">
// 登录页作为启动入口，路由在页面就绪后处理。
</script>

<style>
@import "./styles/modules.css";
@import "./styles/functions.css";
page{background:#f5f7fa;color:#17243a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif;font-size:14px}



view,text,input,textarea,button,picker{box-sizing:border-box}



button{font-size:13px;line-height:1.5;border-radius:10px;padding:12px 15px;background:white;color:#1768ea;margin:0}



button::after{border:0}



button[disabled]{opacity:.45}



input,textarea{width:100%;font-size:14px;border:1px solid #e2e8f1;border-radius:9px;padding:11px;background:#fff;min-height:43px;color:#17243a}



textarea{min-height:80px;height:90px}



input[type=checkbox]{min-height:0}



.page{padding:20px 18px 35px;max-width:780px;margin:0 auto}



.heading{font-size:28px;font-weight:700;letter-spacing:-.6px;margin-bottom:7px}



.subtitle{font-size:12px;color:#7e899b;line-height:1.9;margin-bottom:22px}



.section{font-size:16px;font-weight:600;margin:24px 0 12px}



.card{background:white;border:1px solid #e8edf4;border-radius:15px;padding:16px;margin-bottom:12px}



.row{display:flex;align-items:center;justify-content:space-between;gap:10px}



.gap{display:flex;gap:9px;flex-wrap:wrap}



.gap>view,.gap>text,.gap>input,.gap>button,.gap>picker{flex:1;min-width:0}



.label{font-size:12px;color:#7e899b;margin:0 0 7px;display:block}



.field{margin-bottom:15px}



.muted{font-size:12px;color:#8390a4;line-height:1.8}



.small{font-size:11px}



.title{font-size:15px;font-weight:600;line-height:1.7}



.english{font-size:11px;color:#98a2b2;margin-top:3px;line-height:1.7}



.primary{background:#1768ea;color:white}



.secondary{background:#edf3fe;color:#1768ea}



.danger{background:#fcefee;color:#bd5050}



.full{width:100%;margin-top:12px}



.link{color:#1768ea;font-size:12px;padding:7px 0;display:inline-block}



.badge{display:inline-block;font-size:11px;background:#edf3fc;color:#517db7;border-radius:6px;padding:4px 7px;white-space:nowrap}



.orange{background:#fcf2e4;color:#aa7834}



.green{background:#eaf5ef;color:#298069}



.gray{background:#eff2f7;color:#8190a5}



.notice{background:#eaf1fc;color:#54739f;border-radius:10px;padding:12px;font-size:12px;line-height:1.85;margin:12px 0}



.notice.warn{background:#fff3e2;color:#a77632}



.notice.error{background:#fbeeed;color:#b14e4e}



.empty{text-align:center;color:#8e9aad;font-size:13px;line-height:2;padding:40px 15px}



.divider{height:1px;background:#edf1f6;margin:13px 0}



.pick{padding:11px 10px;border:1px solid #e2e8f1;background:white;border-radius:9px;font-size:13px;min-height:43px}



.pick:after{content:'⌄';float:right;color:#9aa6b8}



.filter{display:flex;gap:8px;flex-wrap:wrap;margin:15px 0}



.filter>picker{flex:1;min-width:90px}



.hero{background:linear-gradient(125deg,#173c75,#24569c);color:white;border-radius:18px;padding:22px;margin:16px 0}



.hero .label{color:#c0cfe5}



.hero .big{font-size:32px;font-weight:600;margin:8px 0}



.metric{display:flex;text-align:center;justify-content:space-between;margin-top:16px}



.metric>view{flex:1}



.metric .value{font-size:25px;font-weight:600}



.metric .label{font-size:11px;margin-top:7px}



.list-row{padding:15px 0;border-bottom:1px solid #edf1f6}



.list-row:last-child{border:0}



.meta{display:flex;gap:15px;flex-wrap:wrap;color:#77869a;font-size:12px;margin-top:10px}



.meta .strong{color:#3e5372}



.tabs{display:flex;gap:20px;border-bottom:1px solid #e4eaf2;margin:15px 0}



.tabs text{padding:10px 0;font-size:13px;color:#8794a7}



.tabs .active{color:#1768ea;border-bottom:2px solid #1768ea}



.check{display:flex;align-items:center;gap:7px;font-size:12px;line-height:1.8;margin:10px 0}



.number{font-variant-numeric:tabular-nums}



.actions{display:flex;gap:9px;margin-top:14px}



.actions button{flex:1}



.issue{border-color:#ebd3a7}



.photo{width:85px;height:85px;object-fit:cover;border-radius:9px;margin:5px}



.safe-bottom{padding-bottom:25px}



.audit{border-left:2px solid #dce6f4;padding-left:13px;margin-bottom:20px}



.audit .muted{overflow-wrap:anywhere}



.step{font-size:12px;color:#7e92ae;margin-bottom:20px}



.required{color:#bd5050}



.count-line{font-size:12px;color:#74829a;line-height:1.8}



.bar{height:6px;background:#edf1f7;border-radius:6px;display:flex;overflow:hidden;margin:11px 0}



.bar view{height:6px;background:#98aac5}



.bar view+view{background:#3277e6}



.small-button{padding:7px 10px;font-size:12px}



.sticky{position:sticky;bottom:0;background:#f5f7fa;padding:10px 0;z-index:3}
</style>
