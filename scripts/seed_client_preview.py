"""Populate the isolated local client preview in one atomic transaction."""
import argparse
import datetime as dt
import os
import subprocess
import uuid
from collections import Counter
from seed_demo_spas import SPAS, PHOTOS, IST, q

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--client', required=True)
args = parser.parse_args()
if not os.environ.get('MYSQL_PWD'):
    parser.error('Set MYSQL_PWD for the local preview.')
client = [args.client, '--default-character-set=utf8mb4', '--host=127.0.0.1', '--port=3307', '--user=root', '--batch', '-N', 'nirvana_spa_test']
def query(sql):
    result = subprocess.run(client, input=sql, text=True, encoding='utf-8', capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr[-1500:])
    return result.stdout.strip()

batch = 'nirvana-client-preview-v1'
if int(query("select count(*) from spa where json_value(meta,'$.demoBatch')="+q(batch))):
    raise SystemExit('Client preview already exists.')
owner, manager = map(int, query("select u.id,m.id from spa_manager m join app_user u on u.id=m.user_id where u.email='owner.preview@example.test'").split())
columns = {}
for row in query("select table_name,column_name from information_schema.columns where table_schema=database()").splitlines():
    table, column = row.split()
    columns.setdefault(table,set()).add(column)
class Ref(str):
    pass
def val(value):
    return str(value) if isinstance(value,Ref) else q(value)
lines = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
counts = Counter()
def add(table, variable=None, **values):
    for field in ('created_at','updated_at'):
        if field in columns[table]:
            values.setdefault(field, Ref('UTC_TIMESTAMP()'))
    if 'version' in columns[table]:
        values.setdefault('version',0)
    lines.append('INSERT INTO '+table+' ('+','.join('`'+c+'`' for c in values)+') VALUES ('+','.join(map(val,values.values()))+');')
    counts[table]+=1
    if variable:
        lines.append('SET @'+variable+'=LAST_INSERT_ID();')
        return Ref('@'+variable)

today=dt.datetime.now(IST).date()
def day(offset): return today+dt.timedelta(days=offset)
def at(offset,hour):
    return dt.datetime.combine(day(offset), dt.time(hour),IST).astimezone(dt.timezone.utc).replace(tzinfo=None)
def image(i):
    photo=PHOTOS[i%len(PHOTOS)][0]
    return f'https://images.pexels.com/photos/{photo}/pexels-photo-{photo}.jpeg?auto=compress&cs=tinysrgb&w=1000'
cities=[('','Gurgaon','Haryana','DLF Phase IV','122009',28.469,77.082),('','Lucknow','Uttar Pradesh','Gomti Nagar','226010',26.856,81.01)]+SPAS
brands=['Amber Courtyard','The Fern House','Juniper Baths','Cedar & Clay','Sandalwood Studio','Quiet Banyan']
services=[('Swedish Massage','Massage',60,220000),('Deep Tissue Massage','Massage',60,280000),('Botanical Facial','Facial',45,160000),('Warm Stone Ritual','Body treatment',60,310000),('Foot Reflexology','Reflexology',45,140000),('Aromatherapy Massage','Massage',60,250000)]
first=['Asha','Ravi','Meera','Dev','Isha','Kabir','Tara','Neel','Anika','Mira','Ishan','Leena']
last=['Shah','Rao','Sen','Kapoor','Sethi','Nair','Joshi','Malik']
reviews=[('A quiet hour','The room was peaceful and my therapist checked the pressure before starting.'),('Lovely facial','I appreciated the gentle pace and the time to unwind afterwards.'),('Thoughtful welcome','Reception helped me choose a treatment and my appointment started on time.'),('A restful afternoon','The warm towels and quiet lounge made the visit feel unhurried.'),('Excellent massage','The therapist focused on my shoulders just as I requested.'),('Would book again','A clean room, a friendly welcome and a lovely cup of tea afterwards.')]
customers=[]
for i in range(96):
    name=first[i%12]+' '+last[i//12]
    email=f'guest.{i+1}@nirvana-preview.example.test'
    u=add('app_user',f'guest{i}',name=name,display_name=name,email=email,username=email,password='!disabled-demo-profile!',active=1,is_active=1,email_verified=0,phone_verified=0,mfa_enabled=0,marketing_opt_in=0,loyalty_points=20*(i%10),timezone='Asia/Kolkata',role_id=2,meta={'demoBatch':batch})
    add('user_roles',user_id=u,role='ROLE_CUSTOMER')
    customers.append(u)

tiers=['SILVER','GOLD','PLATINUM','DIAMOND']
for j,tier in enumerate(tiers):
    if not query('select id from packages where package_type='+q(tier)):
        add('packages',package_type=tier,price_cents=(j+1)*500000,session_count=(j+1)*5,description=f'{(j+1)*5} wellness sessions at participating preview studios.',validity_days=90,is_active=1)
    lines.append(f"SET @pkg{j}=(SELECT id FROM packages WHERE package_type='{tier}');")
for i in range(36):
    _,city,state,area,pin,lat,lon=cities[i%len(cities)]
    branch=i//len(cities)
    brand=brands[(i+branch)%len(brands)]
    title=f'{brand} · {area}'
    spa=add('spa','spa',name=title,city=city,state=state,locality=area,postal_code=pin,address_line1=f'{21+i} Wellness Lane',formatted_address=f'{21+i} Wellness Lane, {area}, {city}',country='India',country_code='IN',latitude=lat+branch*.003,longitude=lon+branch*.002,timezone='Asia/Kolkata',description=f'A neighbourhood wellness studio in {area} with private suites for massage, facials and restorative body treatments. Fictional client preview.',email=f'studio.{i}@nirvana-preview.example.test',owner_name='Preview Spa Owner',spa_manager_id=manager,commission_pct=10,default_currency='INR',tax_percent=0,is_active=1,is_featured=1,is_verified=1,kyc_status='APPROVED',open_time_local='09:00',close_time_local='20:00',allow_therapist_type_selection=1,max_concurrent_services=6,max_advance_booking_days=30,min_notice_minutes=30,amenities=['Tea lounge','Private suites','Shower'],tags=['wellness','massage','facial'],images=[image(i),image(i+1)],meta={'demoBatch':batch,'fictional':True},rating_avg=4.75,rating_count=8,total_bookings=12)
    for j in range(3):
        add('media_asset',spa_id=spa,media_type='IMAGE',position=j+1,object_key=image(i+j),title='Illustrative studio photograph · Pexels')
    for j,d in enumerate(['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'],1):
        add('spa_working_day',spa_id=spa,day_of_week=d)
        add('schedule_rule',spa_id=spa,weekday=j,open_local='09:00',close_local='20:00',is_holiday=0)
    types = ['INDIAN', 'THAI', 'NORTH_EAST']
    for category in types:
        add('spa_therapist_type',spa_id=spa,therapist_type=category)
    for j in range(6):
        add('spa_room',f'room{j}',spa_id=spa,name=['Lotus','Cedar','Fern','Amber','Jade','Pearl'][j]+' Suite',capacity=1,is_active=1)
        name=first[(i+j)%12]+' '+last[(i+j)%8]
        add('therapist',f'ther{j}',spa_id=spa,name=name,display_name=name,type=types[j%3],is_active=1,is_available=1,experience_years=3+j,bio='Specialist in personalised wellness treatments.',languages=['Hindi','English'],currency='INR')
    for j,(name,cat,duration,price) in enumerate(services):
        svc=add('service',f'svc{j}',spa_id=spa,name=name,description=f'A {duration}-minute {name.lower()} in a private suite.',category=cat,currency='INR',duration_min=duration,duration_minutes=duration,base_price_cents=price,price_cents=price,buffer_min=15,gender_allowed='ANY',min_persons=1,max_persons=1,is_active=1,is_visible_on_marketplace=1,service_code=f'CLIENT-{i}-{j}')
        for index in range(6):
            add('therapist_service',therapist_id=Ref(f'@ther{index}'),service_id=svc)
        for offset in range(1,15):
            add('inventory_calendar',spa_id=spa,service_id=svc,service_date=day(offset),available_units=3,base_price_cents=price,currency='INR',locked=0)
            for h in (10,13,16):
                start=at(offset,h)
                add('slot',spa_id=spa,service_id=svc,start_ts=start,end_ts=start+dt.timedelta(minutes=duration),capacity_unit=1,booked_units=0,is_blocked=0,status='OPEN',room_id=Ref(f'@room{j}'),meta={'demoBatch':batch})
    for j,tier in enumerate(tiers):
        add('spa_packages',f'pass{j}',spa_id=spa,level=tier,status='ACTIVE',price=(j+1)*5900,free_sessions_count=(j+1)*3)
        add('service_package_spa',spa_id=spa,package_id=Ref(f'@pkg{j}'))
    plan=add('membership_plan','plan',spa_id=spa,name=brand+' Wellness Club',description='Quarterly member access and priority reservations.',currency='INR',price_cents=390000,duration_days=90,benefits=['Priority reservations','Seasonal rituals'],is_active=1)
    add('coupon',spa_id=spa,code=f'RESETDEMO{i:02}',discount_type='PERCENTAGE',discount_value=10,valid_from=at(-7,9),valid_to=at(40,20),is_active=1)
    add('closure',spa_id=spa,start_ts=at(28,9),end_ts=at(28,10),reason='Studio maintenance',is_recurring=0)
    provider=add('provider_user','provider',name=first[i%12]+' '+last[i%8],display_name=first[i%12],email=f'practitioner.{i}@nirvana-preview.example.test',employee_code=f'DEMO-STAFF-{i}',is_active=1,title='Lead therapist')
    add('provider_leave',spa_id=spa,provider_user_id=provider,start_ts=at(26,9),end_ts=at(26,18),status='APPROVED',reason='Annual leave',approved_by_user_id=owner)
    add('blackout_window',spa_id=spa,provider_id=provider,start_ts=at(26,9),end_ts=at(26,18),reason='Annual leave')
    add('provider_audit_log',spa_id=spa,actor_type='STAFF',actor_id=owner,action='SCHEDULE_UPDATED',details='Published the next two weeks of appointment times.')
    add('admin_action_log',entity_id=spa,entity_type='SPA',action_type='CREATE',reason='Fictional preview studio',actor_user_id=owner)
    user=customers[i]
    add('user_membership',spa_id=spa,user_id=user,plan_id=plan,start_at=at(-10,9),end_at=at(80,9),status='ACTIVE')
    add('user_favorite_spa',user_id=owner,spa_id=spa)
    add('user_favorite_therapist',user_id=user,therapist_id=Ref('@ther0'))
    add('waitlist_entry',spa_id=spa,service_id=Ref('@svc1'),user_id=user,guests=1,active=1,notified=0)
    add('payout',spa_id=spa,amount_cents=198000,currency='INR',status='COMPLETED',payout_date=day(-3),payout_reference=f'DEMO-SETTLED-{i}')
    for k in range(12):
        j=k%6
        guest=owner if k==0 and i%3==0 else customers[(i+k)%96]
        offset=-(k+1) if k<9 else k-8
        start=at(offset,18)
        duration=services[j][2]; price=services[j][3]; end=start+dt.timedelta(minutes=duration)
        state='COMPLETED' if k<8 else 'CANCELLED' if k==8 else 'CONFIRMED'
        booked=int(k!=8)
        slot=add('slot','appointment',spa_id=spa,service_id=Ref(f'@svc{j}'),start_ts=start,end_ts=end,capacity_unit=1,booked_units=booked,is_blocked=0,status='BOOKED' if booked else 'OPEN',room_id=Ref(f'@room{j}'))
        ref=f'DEMO-STUDIO-{i+1:02}-{k+1:02}'
        booking=add('booking','booking',spa_id=spa,user_id=guest,service_id=Ref(f'@svc{j}'),therapist_id=Ref(f'@ther{j}'),slot_id=slot,booking_reference=ref,start_ts=start,end_ts=end,completed_at=end if k<8 else None,created_at=start-dt.timedelta(days=5),updated_at=end if k<9 else at(-2,9),cancelled_at=start-dt.timedelta(hours=2) if k==8 else None,status=state,channel='WEB',booking_source='WEB',currency='INR',guest_count=1,deposit_cents=0,discount_cents=0,tax_cents=0,tip_cents=0,price_cents=price,remainder_cents=0,is_test_booking=1,rating_given=int(k<8),refund_status='COMPLETED' if k==8 else 'NONE',reschedule_count=0,policy_snapshot={},payment_mode='OFFLINE',payment_type='STANDARD',payment_source_type='NORMAL',meta={'demoBatch':batch})
        add('booking_event',booking_id=booking,actor_type='CUSTOMER',actor_id=guest,new_status='CONFIRMED',created_at=start-dt.timedelta(days=5))
        if k<9: add('booking_event',booking_id=booking,actor_type='Spa_MANAGER',actor_id=owner,prev_status='CONFIRMED',new_status=state,created_at=end)
        payment=add('payment','payment',booking_id=booking,amount_cents=price,total_price=price/100,currency='INR',gateway='DEMO',payment_status='REFUNDED' if k==8 else 'CAPTURED',fee_cents=0,refunded_cents=price if k==8 else 0,transaction_id=ref,created_at=start-dt.timedelta(days=4),captured_at=start-dt.timedelta(days=4),meta={'demoBatch':batch})
        add('invoice',booking_id=booking,spa_id=spa,user_id=guest,invoice_number='INV-'+ref,issued_at=start-dt.timedelta(days=4),status='PAID',currency='INR',amount_cents=price,total_cents=price,tax_cents=0,discount_cents=0)
        if k<8:
            title,body=reviews[1 if j==2 else (0,2,3,4,5)[(i+k)%5]]
            add('review',booking_id=booking,spa_id=spa,user_id=guest,rating=4 if k<2 else 5,title=title,text=body,is_visible=1,reported_count=0,helpful_count=k%3,created_at=end+dt.timedelta(hours=2),meta={'demoBatch':batch})
        if k==8:
            add('payment_refund',booking_id=booking,payment_id=payment,requested_by=guest,processed_by=owner,processed_at=end,currency='INR',amount_cents=price,status='COMPLETED',reason='Appointment cancelled in the preview.')
            add('voucher',user_id=guest,booking_id=booking,currency='INR',code='THANKS-'+ref,amount_cents=25000,note='Return visit credit',expires_at=at(30,18))
        if k==0:
            thread=add('message_thread','thread',user_id=guest,booking_id=booking,subject='Arrival instructions',status='RESOLVED',last_message_at=start-dt.timedelta(hours=2),unread_count=0)
            for sender,person,message in [('USER',guest,'How early should I arrive for my treatment?'),('AGENT',owner,'Ten minutes before your appointment is ideal. Tea will be ready in the lounge.')]:
                add('chat_message','message',thread_id=thread,sender_user_id=person,sender_type=sender,content=message,moderation_status='APPROVED')
            add('message_attachment',message_id=Ref('@message'),file_url=image(i),file_name='studio-preview.jpg',content_type='image/jpeg',moderation_status='APPROVED')
            add('notifications',recipient_user_id=guest,type='BOOKING',title='Visit complete',message=f'Your visit to {brand} is complete.',read_at=end)
            add('notification_log',user_id=guest,channel='EMAIL',destination=f'preview.{i}@example.test',status='SENT',sent_at=end,title='Visit complete',message='Your preview visit has been completed.')
            add('support_override',spa_id=spa,booking_id=booking,override_type='BOOKING_NOTE',status='RESOLVED',resolution_notes='Arrival instructions confirmed.')
    if i%6==0:
        add('onboarding_draft',owner_user_id=owner,step_key='BUSINESS_DETAILS',payload={'name':brand+' Annex','city':city},resume_token=uuid.uuid4().hex,expires_at=at(7,18))
    # Preview benefits are redeemed against separate, zero-balance appointments.
    member=owner if i%8==0 else user
    glob=add('user_package_subscription','globalSub',user_id=member,package_id=Ref('@pkg0'),package_type='SILVER',purchase_date=at(-15,9),expiry_date=at(75,9),remaining_sessions=4,purchased_sessions=5,purchased_validity_days=90,amount_cents=500000,currency='INR',status='ACTIVE',payment_status='COMPLETED',payment_reference=f'DEMO-GLOBAL-{i}')
    local=add('user_spa_package_subscription','localSub',user_id=member,spa_id=spa,spa_package_id=Ref('@pass0'),level='SILVER',price_paid=5900,total_sessions=3,remaining_sessions=2,purchase_date=at(-15,9),expiry_date=at(75,9),status='ACTIVE',payment_status='SUCCESS',payment_reference_id=f'DEMO-PASS-{i}',currency='INR')
    if i%6==0:
        org=['Amber Paperworks','Fern Design Group','Riverline Labs','Cedar Textile Studio','Pearl Ceramics','Banyan Press'][i//6]
        corp=add('corporate','corp',name=org,domain=f'partner-{i}.example.test',contact_person=first[i%12],contact_email=f'benefits@partner-{i}.example.test',status='ACTIVE')
        deal=add('corporate_deal','deal',corporate_id=corp,deal_name='Quarterly wellness allowance',description='Three restorative sessions per employee.',coupon_type='MULTI_SERVICE',total_sessions_per_employee=3,start_date=day(-30),end_date=day(60),status='ACTIVE',corporate_payment_status='PAID')
        add('corporate_onboarding_upload',corporate_id=corp,corporate_deal_id=deal,original_file_name='preview-team.csv',status='PROCESSED',total_records=6,success_count=6,failure_count=0)
    employee=add('corporate_employee','employee',corporate_id=corp,user_id=user,employee_email=f'guest.{i+1}@nirvana-preview.example.test',employee_identifier=f'STAFF-{i+1:03}',status='ACTIVE')
    benefit=add('corporate_employee_coupon','benefit',corporate_id=corp,corporate_deal_id=deal,corporate_employee_id=employee,user_id=user,coupon_type='MULTI_SERVICE',total_sessions=3,remaining_sessions=2,start_date=day(-30),expiry_date=day(60),status='ACTIVE')
    for n in range(3):
        recipient=user if n==2 else member
        start=at(-14+n,11); end=start+dt.timedelta(hours=1)
        slot=add('slot','benefitSlot',spa_id=spa,service_id=Ref('@svc0'),start_ts=start,end_ts=end,capacity_unit=1,booked_units=1,is_blocked=0,status='BOOKED')
        key=f'DEMO-PASS-{i}-{n}'
        b=add('booking','redemption',booking_reference=key,spa_id=spa,service_id=Ref('@svc0'),user_id=recipient,slot_id=slot,guest_count=1,start_ts=start,end_ts=end,completed_at=end,status='COMPLETED',created_at=start-dt.timedelta(days=1),updated_at=end,price_cents=0,remainder_cents=0,deposit_cents=0,discount_cents=0,tax_cents=0,tip_cents=0,currency='INR',is_test_booking=1,rating_given=0,refund_status='NONE',reschedule_count=0,policy_snapshot={},payment_source_type='CORPORATE_COUPON' if n==2 else 'NORMAL',package_subscription_id=glob if n==0 else None,corporate_employee_coupon_id=benefit if n==2 else None,payment_type='STANDARD' if n==2 else 'PAID_BY_PACKAGE')
        if n==0: add('user_package_usage_log',subscription_id=glob,user_id=recipient,booking_id=b,spa_id=spa,session_number=1,used_at=end)
        elif n==1: add('user_spa_package_usage_log',user_spa_package_subscription_id=local,user_id=recipient,spa_id=spa,booking_id=b,usage_date=end,session_number=1)
        else: add('corporate_coupon_usage_log',corporate_employee_coupon_id=benefit,user_id=recipient,corporate_id=corp,booking_id=b,spa_id=spa,usage_datetime=end,session_number=1)
    lines.append("UPDATE spa SET total_bookings=15 WHERE id=@spa;")
    if i<8:
        # Historical expired holds have no effect on current availability.
        add('booking_hold',spa_id=spa,slot_id=slot,user_id=user,converted_to_booking=1,expires_at=start-dt.timedelta(hours=2),hold_token=uuid.uuid4().hex,hold_units=1)
faqs=[
 ('How early should I arrive?', 'Arrive ten minutes before your appointment to settle in.', 'Appointments'),
 ('Can I choose my therapist?', 'Choose an available practitioner during checkout where the studio offers therapist selection.', 'Appointments'),
 ('Where can I find my receipt?', 'Open your booking and review the payment history.', 'Payments'),
 ('How do I use a wellness pass?', 'Select an eligible treatment at a participating studio and choose your available pass at checkout.', 'Packages'),
 ('How do I find member benefits?', 'Open My Packages to view your remaining sessions and validity dates.', 'Packages'),
 ('Can I see available treatments?', 'Open the studio profile to explore its services, prices and appointment times.', 'Treatments'),
 ('How do I share feedback?', 'Open a completed booking to rate your visit and write a review.', 'Reviews'),
 ('How do I find a nearby studio?', 'Use Search nearby and allow location access to explore Google Places listings.', 'Discovery')]
for i,(question,answer,category) in enumerate(faqs):
    add('faq',question=question,answer=answer,category=category,is_active=1)
    add('content_variant',experiment_key='preview_guest_guide',variant_key='tip_'+str(i),content=answer,is_active=1,locale='en')
    add('notification_template',code='PREVIEW_GUIDE_'+str(i),channel='EMAIL',name=question,subject=question,body=answer,locale='en',enabled=0)
lines.append('COMMIT;')
query('\n'.join(lines))
print('Inserted',sum(counts.values()),'records across',len(counts),'tables.')
for table,count in sorted(counts.items()): print(table,count)
