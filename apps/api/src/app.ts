import express from "express";
import helmet from "helmet";
export const createApp=()=>{const app=express();app.disable("x-powered-by");app.use(helmet());app.use(express.json({limit:"1mb"}));app.get("/health",(_req,res)=>res.status(200).json({status:"ok",service:"rackpay-api"}));return app;};
