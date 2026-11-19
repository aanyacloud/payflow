--
-- PostgreSQL database dump
--

\restrict WRaTfjiSOGbpT9OY7xnwWUEpwuezcIrw1Aqn7y3G5qbrAgIJXxuuNAK9SK6Us2A

-- Dumped from database version 17.12 (Debian 17.12-1.pgdg13+1)
-- Dumped by pg_dump version 17.12 (Debian 17.12-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: payment_status_history; Type: TABLE; Schema: public; Owner: payflow
--

CREATE TABLE public.payment_status_history (
    id uuid NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    new_status character varying(20) NOT NULL,
    previous_status character varying(20) NOT NULL,
    payment_id uuid NOT NULL
);


ALTER TABLE public.payment_status_history OWNER TO payflow;

--
-- Name: payments; Type: TABLE; Schema: public; Owner: payflow
--

CREATE TABLE public.payments (
    id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    idempotency_key character varying(100)
);


ALTER TABLE public.payments OWNER TO payflow;

--
-- Name: refund_status_history; Type: TABLE; Schema: public; Owner: payflow
--

CREATE TABLE public.refund_status_history (
    id uuid NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    new_status character varying(20) NOT NULL,
    previous_status character varying(20) NOT NULL,
    refund_id uuid NOT NULL
);


ALTER TABLE public.refund_status_history OWNER TO payflow;

--
-- Name: refunds; Type: TABLE; Schema: public; Owner: payflow
--

CREATE TABLE public.refunds (
    id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    payment_id uuid NOT NULL
);


ALTER TABLE public.refunds OWNER TO payflow;

--
-- Data for Name: payment_status_history; Type: TABLE DATA; Schema: public; Owner: payflow
--

COPY public.payment_status_history (id, changed_at, new_status, previous_status, payment_id) FROM stdin;
178150b0-fac6-4907-b588-3ba51be7af23	2026-11-18 18:06:45.535307+00	PROCESSING	PENDING	75d95a2f-ac77-4373-9d6b-c3c08904020d
51993f7f-0289-4e45-beaf-bb7e96562be3	2026-11-18 18:07:09.130009+00	SUCCESS	PROCESSING	75d95a2f-ac77-4373-9d6b-c3c08904020d
\.


--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: payflow
--

COPY public.payments (id, amount, created_at, currency, status, idempotency_key) FROM stdin;
4d28c4e0-3798-4c8b-ab46-4a466a1dfd11	499.99	2026-11-18 16:24:26.425491+00	INR	PENDING	\N
5851d493-3ed3-4e9d-ae88-93de7637dd96	999.50	2026-11-18 17:02:12.826817+00	INR	PENDING	\N
a418c127-b246-43e8-a546-bf8339b67705	499.99	2026-11-18 17:22:27.824609+00	INR	PENDING	payflow-test-001
af1da77c-63ac-49a9-a176-fa59af24b393	799.99	2026-11-18 17:27:38.235639+00	INR	PENDING	concurrent-test-001
75d95a2f-ac77-4373-9d6b-c3c08904020d	499.99	2026-11-18 17:23:47.573237+00	INR	SUCCESS	payflow-test-002
\.


--
-- Data for Name: refund_status_history; Type: TABLE DATA; Schema: public; Owner: payflow
--

COPY public.refund_status_history (id, changed_at, new_status, previous_status, refund_id) FROM stdin;
031c8553-2de8-46cb-8208-2b33ec370d89	2026-11-18 18:33:10.821678+00	PROCESSING	PENDING	8de74273-b207-4f2e-abeb-bb003ad589ef
7102b73d-43df-4bd1-81d6-589b1e2a2e7f	2026-11-18 18:33:18.737766+00	SUCCESS	PROCESSING	8de74273-b207-4f2e-abeb-bb003ad589ef
\.


--
-- Data for Name: refunds; Type: TABLE DATA; Schema: public; Owner: payflow
--

COPY public.refunds (id, amount, created_at, idempotency_key, status, payment_id) FROM stdin;
c6a050d7-8f01-4fd6-9a19-411066774312	200.00	2026-11-18 18:18:55.502307+00	refund-test-001	SUCCESS	75d95a2f-ac77-4373-9d6b-c3c08904020d
8de74273-b207-4f2e-abeb-bb003ad589ef	299.99	2026-11-18 18:19:19.05069+00	refund-test-002	SUCCESS	75d95a2f-ac77-4373-9d6b-c3c08904020d
\.


--
-- Name: payment_status_history payment_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.payment_status_history
    ADD CONSTRAINT payment_status_history_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: refund_status_history refund_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.refund_status_history
    ADD CONSTRAINT refund_status_history_pkey PRIMARY KEY (id);


--
-- Name: refunds refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);


--
-- Name: refunds uk_refund_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT uk_refund_idempotency_key UNIQUE (idempotency_key);


--
-- Name: payments ukj4pdqn1207fuhx2gybp1ue799; Type: CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT ukj4pdqn1207fuhx2gybp1ue799 UNIQUE (idempotency_key);


--
-- Name: idx_payment_history_payment_id; Type: INDEX; Schema: public; Owner: payflow
--

CREATE INDEX idx_payment_history_payment_id ON public.payment_status_history USING btree (payment_id);


--
-- Name: idx_refund_history_refund_id; Type: INDEX; Schema: public; Owner: payflow
--

CREATE INDEX idx_refund_history_refund_id ON public.refund_status_history USING btree (refund_id);


--
-- Name: idx_refunds_payment_id; Type: INDEX; Schema: public; Owner: payflow
--

CREATE INDEX idx_refunds_payment_id ON public.refunds USING btree (payment_id);


--
-- Name: refund_status_history fk3bew96t1i3obu62u6rssj07di; Type: FK CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.refund_status_history
    ADD CONSTRAINT fk3bew96t1i3obu62u6rssj07di FOREIGN KEY (refund_id) REFERENCES public.refunds(id);


--
-- Name: payment_status_history fkpo8yhsq5qm6p4cflhsfbl5kog; Type: FK CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.payment_status_history
    ADD CONSTRAINT fkpo8yhsq5qm6p4cflhsfbl5kog FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: refunds fkpt9ic0j1y6xwlej99wnynvnpy; Type: FK CONSTRAINT; Schema: public; Owner: payflow
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT fkpt9ic0j1y6xwlej99wnynvnpy FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- PostgreSQL database dump complete
--

\unrestrict WRaTfjiSOGbpT9OY7xnwWUEpwuezcIrw1Aqn7y3G5qbrAgIJXxuuNAK9SK6Us2A

