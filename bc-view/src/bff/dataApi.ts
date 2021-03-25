import express from "express";
import { makeRequest } from "../common/axiosUtils";
import { bcConfig } from "../common/config";
import { AxiosRequestConfig } from "axios";
import { svcUrl } from "../server/utils";

export const getData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, bcConfig.bcData).toString(),
    headers: req.headers,
    method: "GET",
  } as AxiosRequestConfig;
  //logger.debug("BFF- Get: " + opts.url);
  await makeRequest(req, opts, res);
};

export const deleteData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, bcConfig.bcData).toString(),
    headers: req.headers,
    method: "DELETE",
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const patchData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, bcConfig.bcData).toString(),
    headers: req.headers,
    data: req.body,
    method: "PATCH",
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const postData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, bcConfig.bcData).toString(),
    headers: req.headers,
    data: req.body,
    method: "POST",
  } as AxiosRequestConfig;
  //logger.debug("calling %s %s", opts.url, req.headers);
  await makeRequest(req, opts, res);
};
