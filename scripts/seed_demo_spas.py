"""Add fictional demo data only to the isolated local nirvana_spa_test database.
Requires MYSQL_PWD and --client pointing to mariadb.exe. Existing data is not changed.
"""
import argparse, datetime as dt, json, os, subprocess, urllib.request, concurrent.futures
from pathlib import Path
BATCH='nirvana-demo-ten-2026-10'
LICENSE='https://www.pexels.com/license/'
PHOTOS=[
(3230236,'person-doing-massage'),(6663378,'a-woman-having-a-massage'),
(6187646,'a-person-putting-pebbles-on-client-s-back'),(18120173,'women-getting-a-massage-with-stones'),
(5378219,'person-holding-hot-stones'),(6560288,'a-person-massaging-a-client-with-a-stone'),
(6629521,'woman-having-a-massage-with-massage-stones'),(5240636,'crop-person-with-hot-stones-on-back-during-massage-procedure'),
(3736278,'towels-on-counter'),(7795398,'brown-bottles-and-dispensers-on-folded-towels')]
SPAS=[
('Lotus Calm','Bengaluru','Karnataka','Indiranagar','560038',12.9784,77.6408),
('Sea Breeze','Mumbai','Maharashtra','Bandra West','400050',19.0607,72.8362),
('Urban Serenity','New Delhi','Delhi','Greater Kailash','110048',28.5494,77.2408),
('Rosewood Retreat','Pune','Maharashtra','Koregaon Park','411001',18.5362,73.8939),
('Pearl Wellness','Hyderabad','Telangana','Banjara Hills','500034',17.4126,78.4482),
('Coastal Bliss','Chennai','Tamil Nadu','Adyar','600020',13.0012,80.2565),
('Pink Lotus','Jaipur','Rajasthan','C Scheme','302001',26.9124,75.7873),
('Riverstone','Kolkata','West Bengal','Ballygunge','700019',22.5287,88.3657),
('Palm Haven','Kochi','Kerala','Panampilly Nagar','682036',9.9558,76.3004),
('Himalayan Pause','Chandigarh','Chandigarh','Sector 17','160017',30.7410,76.7805)]
NAMES=[('Anaya','Rohan'),('Meera','Arjun'),('Kavya','Dev'),('Isha','Kabir'),('Nisha','Aarav'),('Diya','Vikram'),('Tara','Aditya'),('Riya','Neil'),('Maya','Kiran'),('Sana','Varun')]
IST=dt.timezone(dt.timedelta(hours=5,minutes=30))
def q(v):
 if v is None:return 'NULL'
 if isinstance(v,bool):return '1' if v else '0'
 if isinstance(v,(int,float)):return str(v)
 if isinstance(v,(dict,list)):v=json.dumps(v,ensure_ascii=True)
 return "'"+str(v).replace('\\','\\\\').replace("'","''")+"'"
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--client',required=True);ap.add_argument('--start-date',default=dt.datetime.now(IST).date().isoformat());ap.add_argument('--report',required=True);args=ap.parse_args()
 if not os.environ.get('MYSQL_PWD'):raise SystemExit('Set MYSQL_PWD locally before running; do not store it in the script.')
 command=[args.client,'-h','127.0.0.1','-P3307','-uroot','--batch','--skip-column-names','nirvana_spa_test']
 def sql(value):
  result=subprocess.run(command,input=value,encoding='utf-8',capture_output=True)
  if result.returncode:raise RuntimeError(result.stderr)
  return result.stdout.strip()
 existing=int(sql("SELECT COUNT(*) FROM spa WHERE JSON_UNQUOTE(JSON_EXTRACT(meta,'$.demoBatch'))="+q(BATCH)))
 if existing:raise SystemExit(f'Batch already has {existing} spas; no changes made.')
 manager=sql("SELECT m.id FROM spa_manager m JOIN app_user u ON u.id=m.user_id WHERE u.email='owner.preview@example.test'")
 if not manager.isdigit():raise SystemExit('Expected preview owner is missing or ambiguous; no changes made.')
 photos=[{'url':f'https://images.pexels.com/photos/{pid}/pexels-photo-{pid}.jpeg?auto=compress&cs=tinysrgb&w=1200','source':f'https://www.pexels.com/photo/{slug}-{pid}/','license':LICENSE} for pid,slug in PHOTOS]
 def verify(photo):
  req=urllib.request.Request(photo['url'],method='HEAD',headers={'User-Agent':'Nirvana-demo-seed/1.0'})
  with urllib.request.urlopen(req,timeout=30) as response:
   assert response.status==200 and response.headers.get('Content-Type','').startswith('image/'), photo['source']
 with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:list(pool.map(verify,photos))
 lines=['SET NAMES utf8mb4;','START TRANSACTION;']; now=dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S')
 def insert(table,values,var=None,timestamps=True):
  if timestamps:values={'created_at':now,'updated_at':now,**values}
  lines.append('INSERT INTO '+table+' ('+','.join('`'+k+'`' for k in values)+') VALUES ('+','.join(str(v)[4:] if isinstance(v,str) and v.startswith('SQL:') else q(v) for v in values.values())+');')
  if var:lines.append('SET @'+var+' = LAST_INSERT_ID();')
 start=dt.date.fromisoformat(args.start_date); report=[]
 for i,(brand,city,state,locality,pin,lat,lon) in enumerate(SPAS):
  title=f'{brand} Spa (Demo)'; photo=photos[i]; address=f'{10+i} Demo Wellness Lane, {locality}'
  insert('spa',dict(name=title,description=f'Fictional demo spa in {locality}, {city}. Explore relaxing treatments, a quiet lounge and personalised wellness sessions. Stock photographs are illustrative; this is not a real business.',address_line1=address,city=city,state=state,country='India',country_code='IN',postal_code=pin,locality=locality,latitude=lat,longitude=lon,formatted_address=f'{address}, {city}',timezone='Asia/Kolkata',phone='0000000000',email=f'demo.spa.{i+1}@example.test',owner_name='Preview Spa Owner',spa_manager_id=int(manager),is_active=True,is_verified=True,is_featured=True,kyc_status='APPROVED',kyc_approved_at=now,default_currency='INR',tax_percent=18,commission_pct=10,rating_avg=0,rating_count=0,total_bookings=0,open_time_local='09:00:00',close_time_local='20:00:00',allow_therapist_selection=True,allow_therapist_type_selection=False,max_concurrent_services=2,max_advance_booking_days=30,min_notice_minutes=60,amenities=['Private treatment rooms','Shower','Herbal tea','Relaxation lounge'],tags=['demo','wellness','massage'],images=[photo['url'],photos[(i+1)%10]['url']],meta={'demoBatch':BATCH,'fictional':True,'imageCredits':[photo,photos[(i+1)%10]]},version=0),'spa')
  for pos,image in enumerate([photo,photos[(i+1)%10]],1):insert('media_asset',dict(spa_id='SQL:@spa',object_key=image['url'],media_type='IMAGE',position=pos,title='Illustrative stock photo · Pexels license'))
  for weekday,day in enumerate(['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'],1):
   insert('spa_working_day',dict(spa_id='SQL:@spa',day_of_week=day),timestamps=False)
   insert('schedule_rule',dict(spa_id='SQL:@spa',weekday=weekday,open_local='09:00:00',close_local='20:00:00',is_holiday=False,version=0,note='Demo opening schedule'),timestamps=False)
  for room in range(1,3):insert('spa_room',dict(spa_id='SQL:@spa',name=f'Treatment Room {room}',code=f'DEMO-{i+1}-R{room}',capacity=1,is_active=True,version=0),'room'+str(room))
  for t,name in enumerate(NAMES[i],1):insert('therapist',dict(spa_id='SQL:@spa',name=name+' (Demo)',display_name=name+' (Demo)',type='massage',bio='Fictional therapist profile for testing spa bookings.',currency='INR',experience_years=3+t,is_active=True,is_available=True,rating_avg=0,rating_count=0,languages=['English','Hindi'],version=0),'therapist'+str(t))
  for j,(service,duration,price) in enumerate([('Swedish Relaxation Massage',60,150000+i*5000),('Deep Tissue Massage',60,190000+i*5000),('Aromatherapy Massage',60,170000+i*5000)]):
   insert('service',dict(spa_id='SQL:@spa',name=service,description='Demo wellness treatment with a short consultation and private treatment room.',duration_min=duration,duration_minutes=duration,buffer_min=15,base_price_cents=price,price_cents=price,currency='INR',gender_allowed='ANY',is_active=True,is_visible_on_marketplace=True,min_persons=1,max_persons=1,category='massage',service_code=f'DEMO10-{i+1}-{j+1}',version=0),'service')
   for t in range(1,3):insert('therapist_service',dict(therapist_id=f'SQL:@therapist{t}',service_id='SQL:@service'),timestamps=False)
   for d in range(7):
    date=start+dt.timedelta(days=d)
    insert('inventory_calendar',dict(spa_id='SQL:@spa',service_id='SQL:@service',service_date=date.isoformat(),available_units=12,base_price_cents=price,currency='INR',locked=False,note='Demo capacity; two therapists and six appointment times'))
    for h in [9,10.5,12,14,15.5,17]:
     begin=dt.datetime.combine(date,dt.time(int(h),int(h%1*60)),IST).astimezone(dt.timezone.utc);end=begin+dt.timedelta(minutes=duration)
     insert('slot',dict(spa_id='SQL:@spa',service_id='SQL:@service',start_ts=begin.strftime('%Y-%m-%d %H:%M:%S'),end_ts=end.strftime('%Y-%m-%d %H:%M:%S'),capacity_unit=2,booked_units=0,is_blocked=False,status='OPEN',version=0,meta={'demoBatch':BATCH}))
  for level,price,sessions in [('SILVER',3999+i*100,3),('GOLD',7499+i*100,6)]:insert('spa_packages',dict(spa_id='SQL:@spa',level=level,price=price,free_sessions_count=sessions,status='ACTIVE',version=0))
  report.append({'name':title,'city':city,'images':[photo,photos[(i+1)%10]],'services':3,'therapists':2,'packages':2,'rooms':2,'slots':126})
 lines+=['COMMIT;',"SELECT id,name,city FROM spa WHERE JSON_UNQUOTE(JSON_EXTRACT(meta,'$.demoBatch'))="+q(BATCH)+' ORDER BY id;']
 output=sql('\n'.join(lines));Path(args.report).write_text(json.dumps({'batch':BATCH,'database':'nirvana_spa_test','availabilityFrom':start.isoformat(),'availabilityThrough':(start+dt.timedelta(days=6)).isoformat(),'spas':report,'databaseRows':output},indent=2),encoding='utf-8');print(output);print('Created 10 spas, 30 services, 20 therapists, 20 packages, 20 rooms, 20 photo references, 210 inventory days and 1260 slots.')
if __name__=='__main__':main()

