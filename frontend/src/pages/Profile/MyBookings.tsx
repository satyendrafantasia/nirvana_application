import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cancelBooking, getUserBookings } from '../../api/bookingApi';
import Button from '../../components/common/Button';
import Spinner from '../../components/common/Spinner';
import { BookingSummary } from '../../types/booking';
import { showToast } from '../../components/common/Toast';

const MyBookings = () => {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['my-bookings'], queryFn: getUserBookings });

  const mutation = useMutation({
    mutationFn: (bookingId: string) => cancelBooking(bookingId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] });
      showToast('Booking cancelled', 'success');
    },
    onError: () => showToast('Unable to cancel booking', 'error')
  });

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <h1 className="text-3xl font-bold text-slate-900">My bookings</h1>
      <p className="text-slate-600">Manage upcoming and past appointments.</p>

      {isLoading && (
        <div className="mt-6">
          <Spinner />
        </div>
      )}

      <div className="mt-6 space-y-4">
        {data?.map((booking: BookingSummary) => (
          <div key={booking.id} className="card flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm text-slate-500">{booking.spaName}</p>
              <p className="text-lg font-semibold text-slate-900">{booking.service.name}</p>
              <p className="text-sm text-slate-500">
                {new Date(booking.slot).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
              </p>
              <p className="text-xs uppercase tracking-wide text-primary-700">{booking.status}</p>
            </div>
            <div className="flex gap-2">
              <Button variant="secondary">Reschedule</Button>
              <Button variant="ghost" onClick={() => mutation.mutate(booking.id)}>
                Cancel
              </Button>
            </div>
          </div>
        ))}
        {!isLoading && (!data || data.length === 0) && <p className="text-sm text-slate-500">No bookings yet.</p>}
      </div>
    </div>
  );
};

export default MyBookings;
