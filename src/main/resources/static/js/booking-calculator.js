// src/main/resources/static/js/booking-calculator.js
document.addEventListener('DOMContentLoaded', function () {
    const pickupDate = document.querySelector('input[name="pickupDate"]');
    const returnDate = document.querySelector('input[name="returnDate"]');
    const pricePerDay = parseFloat(document.querySelector('.car-summary strong').innerText.replace(/[^0-9.-]+/g,""));

    function updateTotal() {
        if (pickupDate.value && returnDate.value) {
            const start = new Date(pickupDate.value);
            const end = new Date(returnDate.value);
            const days = Math.ceil((end - start) / (1000 * 60 * 60 * 24));
            if (days > 0) {
                document.getElementById('days-count').textContent = days;
                document.getElementById('total-price').textContent = formatCurrency(pricePerDay * days);
            }
        }
    }

    function formatCurrency(amount) {
        return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
    }

    pickupDate.addEventListener('change', updateTotal);
    returnDate.addEventListener('change', updateTotal);
});