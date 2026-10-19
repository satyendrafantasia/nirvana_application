"""Exercise real local APIs against an isolated database (creates two disposable customers and bookings).
Run only with the local-spa profile. Set SPA_TEST_DATE to a seeded future slot date.
"""
import json, os, time, urllib.request, urllib.error
BASE=os.environ.get('SPA_TEST_API','http://localhost:8081')
DATE=os.environ['SPA_TEST_DATE']
checks=[]
def call(method,path,body=None,token=None,expected=200):
    headers={'Content-Type':'application/json','User-Agent':'NirvanaLocalSmoke/1.0'}
    if token:headers['Authorization']='Bearer '+token
    request=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(request) as r:status=r.status;raw=r.read()
    except urllib.error.HTTPError as e:status=e.code;raw=e.read()
    data=json.loads(raw) if raw else None
    assert status==expected,(method,path,status,data)
    checks.append(f'{method} {path}: {status}')
    return data
stamp=str(time.time_ns())
def register(suffix):
    email=f'spa.{stamp}.{suffix}@example.test'
    payload={'username':email,'password':'SpaLocalTest123!','name':'Test','lastName':'Guest','roleType':'CUSTOMER','acceptPrivacyPolicy':True,'marketingOptIn':False}
    auth=call('POST','/api/auth/register',payload)
    login=call('POST','/api/auth/login',{'username':email,'password':payload['password']})
    assert login['userId']==auth['userId']
    return login['accessToken']
call('GET','/api/me/bookings',expected=401)
first=register('a');second=register('b')
call('POST','/api/auth/register',{'username':f'admin.{stamp}@example.test','password':'SpaLocalTest123!','name':'Test','lastName':'Guest','roleType':'ADMIN','acceptPrivacyPolicy':True},expected=403)
call('GET','/api/auth/me',token=first)
spas=call('GET','/api/spas/search',token=first)['content'];assert spas
spa=spas[0]['id']
call('GET',f'/api/spas/{spa}',token=first)
services=call('GET',f'/api/spas/{spa}/services',token=first);assert services
service=services[0]['id']
slots=call('GET',f'/api/spas/{spa}/services/{service}/slots?date={DATE}&guests=1',token=first);assert len(slots)>=2
booking=call('POST','/api/bookings',{'spaId':spa,'serviceId':service,'slotId':slots[0]['slotId'],'guests':1,'paymentMode':'OFFLINE','bookingSource':'WEB'},first)
assert booking['status']=='CONFIRMED'
bid=booking['bookingId']
other=call('POST','/api/bookings',{'spaId':spa,'serviceId':service,'slotId':slots[0]['slotId'],'guests':1,'paymentMode':'OFFLINE','bookingSource':'WEB'},second)
call('POST','/api/bookings',{'spaId':spa,'serviceId':service,'slotId':slots[0]['slotId'],'guests':1,'paymentMode':'OFFLINE','bookingSource':'WEB'},first,expected=409)
call('POST',f'/api/bookings/{bid}/cancel',{},second,expected=404)
call('POST',f'/api/bookings/{other["bookingId"]}/cancel',{},second)
mine=call('GET','/api/me/bookings',token=first)['page']['content'];assert any(b['bookingId']==bid for b in mine)
for endpoint in ['receipt','timeline']:
    call('GET',f'/api/bookings/{bid}/payments/razorpay/{endpoint}',token=second,expected=403)
call('POST',f'/api/bookings/{bid}/payments/upi/confirm',{'paymentIntentId':'fake','transactionReference':'fake','payerVpa':'test@upi','success':True},first,expected=409)
call('POST',f'/api/bookings/{bid}/reschedule',{'targetSlotId':slots[1]['slotId']},first)
call('POST',f'/api/bookings/{bid}/cancel',{},first)
mine=call('GET','/api/me/bookings',token=first)['page']['content'];assert next(b for b in mine if b['bookingId']==bid)['status']=='CANCELLED'
call('GET',f'/api/spas/{spa}/reviews',token=first)
call('GET','/api/faqs',token=first)
print('\n'.join(checks));print(f'PASS: {len(checks)} live HTTP checks; booking {bid} created, rescheduled and cancelled.')
