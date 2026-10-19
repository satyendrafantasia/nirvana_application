import {beforeEach,expect,it,vi} from 'vitest';
import client from './client';
import {payWithRazorpay,resumePayment} from './paymentApi';
import {PaymentIntent,RazorpayResult} from '../types/payment';
const intent:PaymentIntent={bookingId:1,razorpayKeyId:'test',razorpayOrderId:'order_1',amount:1250,currency:'INR'};
let callback:(r:RazorpayResult)=>void;
let options:Record<string,unknown>;
const result={razorpay_order_id:'order_1',razorpay_payment_id:'pay_1',razorpay_signature:'signature'};
beforeEach(()=>{
 const values=new Map<string,string>();
 vi.stubGlobal('sessionStorage',{getItem:(k:string)=>values.get(k)||null,setItem:(k:string,v:string)=>values.set(k,v),removeItem:(k:string)=>values.delete(k)});
 vi.stubGlobal('localStorage',{getItem:()=>null});
 vi.stubGlobal('window',{Razorpay:class {constructor(o:Record<string,unknown>){options=o;callback=o.handler as typeof callback;}open(){}on(){}}});
});
it('converts major-unit amount and waits for backend confirmation',async()=>{
 client.defaults.adapter=async config=>({data:{status:'CONFIRMED',refundStatus:'NONE'},status:200,statusText:'OK',headers:{},config});
 const pending=payWithRazorpay(intent);expect(options.amount).toBe(125000);callback(result);
 expect(await pending).toEqual({status:'CONFIRMED',refundStatus:'NONE'});
});
it('retries verification without opening another checkout after a failed confirmation',async()=>{
 client.defaults.adapter=async()=>{throw new Error('Capture pending');};
 const pending=payWithRazorpay(intent);const rejected=expect(pending).rejects.toThrow('Capture pending');callback(result);await rejected;
 const checkout=vi.fn();vi.stubGlobal('window',{Razorpay:checkout});
 client.defaults.adapter=async config=>({data:{status:'CONFIRMED',refundStatus:'NONE'},status:200,statusText:'OK',headers:{},config});
 expect(await resumePayment('1')).toMatchObject({status:'CONFIRMED'});expect(checkout).not.toHaveBeenCalled();
});
it('preserves server cancellation status after a late capture',async()=>{
 client.defaults.adapter=async config=>({data:{status:'CANCELLED',refundStatus:'REQUESTED'},status:200,statusText:'OK',headers:{},config});
 const pending=payWithRazorpay(intent);callback(result);expect(await pending).toEqual({status:'CANCELLED',refundStatus:'REQUESTED'});
});
