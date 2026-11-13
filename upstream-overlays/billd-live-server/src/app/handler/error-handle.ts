import { ParameterizedContext } from 'koa';
import { CustomError } from '@/model/customError.model';

const errorHandler = (error: unknown, ctx: ParameterizedContext) => {
  const known = error instanceof CustomError && error.statusCode >= 400 && error.statusCode <= 599;
  const status = known ? error.statusCode : 500;
  ctx.status = status;
  ctx.body = {
    code: known ? error.errorCode : 10000,
    errorCode: known ? error.errorCode : 10000,
    message: known ? error.message : '服务暂时不可用，请稍后重试',
  };
  // Do not print request credentials, payment signatures or database error payloads.
  const path = ctx.path.replace(/^\/openim\/hooks\/[^/]+(?=\/callbackBeforeSendSingleMsgCommand$)/, '/openim/hooks/[redacted]');
  console.error('Request failed', { status, method: ctx.method, path });
};

export default errorHandler;
