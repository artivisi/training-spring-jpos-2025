document.addEventListener('DOMContentLoaded', () => {
    const typeSelect = document.getElementById('type');
    const amountField = document.getElementById('amountField');
    const atmForm = document.getElementById('atmForm');

    typeSelect.addEventListener('change', (e) => {
        if (e.target.value === 'WITHDRAWAL') {
            amountField.classList.remove('hidden');
            document.getElementById('amount').required = true;
        } else {
            amountField.classList.add('hidden');
            document.getElementById('amount').required = false;
        }
    });

    atmForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const formData = {
            pan: document.getElementById('pan').value,
            accountNumber: document.getElementById('accountNumber').value,
            pin: document.getElementById('pin').value,
            type: document.getElementById('type').value,
            amount: document.getElementById('type').value === 'WITHDRAWAL' ?
                    parseFloat(document.getElementById('amount').value) : null,
            terminalId: 'ATM00001'
        };

        const resultDiv = document.getElementById('result');

        try {
            const response = await fetch('/atm/transaction', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });

            const data = await response.json();

            if (data.responseCode === '00') {
                resultDiv.className = 'mb-4 p-4 rounded bg-green-100 text-green-700';
                resultDiv.innerHTML = `
                    <p class="font-bold">${data.responseMessage}</p>
                    <p>Balance: $${data.balance}</p>
                    ${data.amount ? `<p>Amount: $${data.amount}</p>` : ''}
                    <p class="text-sm">Transaction ID: ${data.transactionId || 'N/A'}</p>
                `;
            } else {
                resultDiv.className = 'mb-4 p-4 rounded bg-red-100 text-red-700';
                resultDiv.innerHTML = `
                    <p class="font-bold">Transaction Failed</p>
                    <p>${data.responseMessage}</p>
                    <p class="text-sm">Code: ${data.responseCode}</p>
                `;
            }

            resultDiv.classList.remove('hidden');
        } catch (error) {
            resultDiv.className = 'mb-4 p-4 rounded bg-red-100 text-red-700';
            resultDiv.textContent = 'Error: ' + error.message;
            resultDiv.classList.remove('hidden');
        }
    });
});
