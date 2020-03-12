import express from "express";
import { makeRequest, svcUrl } from "../common/axiosUtils";
import { runtimeConfig } from "../config";
import { AxiosRequestConfig } from "axios";

export const getData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcData).toString(),
    headers: req.headers,
    method: "GET"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const deleteData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcData).toString(),
    headers: req.headers,
    method: "DELETE"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const patchData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcData).toString(),
    headers: req.headers,
    data: req.body,
    method: "PATCH"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const postData = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcData).toString(),
    headers: req.headers,
    data: req.body,
    method: "POST"
  } as AxiosRequestConfig;
  //logger.debug("calling %s %s", opts.url, req.headers);
  await makeRequest(req, opts, res);
};
